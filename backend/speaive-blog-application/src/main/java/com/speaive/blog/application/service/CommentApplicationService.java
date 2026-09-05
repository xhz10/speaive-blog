package com.speaive.blog.application.service;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.application.port.out.ai.AiCommentGeneration;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiCommentPrompt;
import com.speaive.blog.application.port.out.ai.AiSummaryGeneration;
import com.speaive.blog.application.port.out.ai.AiSummaryGenerationPort;
import com.speaive.blog.application.port.out.ai.AiSummaryPrompt;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.persistence.AgentRunRepository;
import com.speaive.blog.application.port.out.persistence.CommentQueryScope;
import com.speaive.blog.application.port.out.persistence.CommentRepository;
import com.speaive.blog.application.port.out.persistence.PostAiSummaryRepository;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.comment.AiSummaryCoverageResult;
import com.speaive.blog.application.result.comment.CommentListResult;
import com.speaive.blog.application.result.comment.CommentResult;
import com.speaive.blog.application.result.comment.PostAiSummaryResult;
import com.speaive.blog.application.result.post.AuthorResult;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.agent.AgentRun;
import com.speaive.blog.domain.comment.Comment;
import com.speaive.blog.domain.comment.CommentStatus;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostAiSummary;
import com.speaive.blog.domain.post.PostSummary;
import com.speaive.blog.domain.post.PostVisibility;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 评论与回复用例编排，同时协调文章记忆摘要和生成审计。AI 调用放在数据库事务之外，生成结果先待审核，审核状态由 Comment 决定。
 */
public final class CommentApplicationService implements CommentUseCase {
    private static final Duration STALE_RUN_AFTER = Duration.ofMinutes(15);
    private static final int MAX_RELATED_POSTS = 8;

    private final PostRepository posts;
    private final PostAiSummaryRepository summaries;
    private final AgentRepository agents;
    private final CommentRepository comments;
    private final AgentRunRepository runs;
    private final AiCommentGenerationPort commentAi;
    private final AiSummaryGenerationPort summaryAi;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CommentApplicationService(
            PostRepository posts,
            PostAiSummaryRepository summaries,
            AgentRepository agents,
            CommentRepository comments,
            AgentRunRepository runs,
            AiCommentGenerationPort commentAi,
            AiSummaryGenerationPort summaryAi,
            TransactionRunner transactions,
            Clock clock) {
        this.posts = Objects.requireNonNull(posts, "posts");
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.agents = Objects.requireNonNull(agents, "agents");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.commentAi = Objects.requireNonNull(commentAi, "commentAi");
        this.summaryAi = Objects.requireNonNull(summaryAi, "summaryAi");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CommentListResult listStudioComments(String postSlug) {
        return withDomainErrors(() -> transactions.required(() -> list(requiredPost(postSlug, PostQueryScope.STUDIO),
                CommentQueryScope.STUDIO)));
    }

    @Override
    public CommentListResult listPublishedComments(String postSlug) {
        return withDomainErrors(() -> transactions.required(() -> list(requiredPost(postSlug, PostQueryScope.PUBLISHED),
                CommentQueryScope.PUBLISHED)));
    }

    @Override
    public CommentResult generateAiComment(String postSlug, String agentId) {
        return generate(postSlug, agentId, null);
    }

    @Override
    public CommentResult generateAiReply(String commentId, String agentId) {
        ReplySeed seed = withDomainErrors(() -> transactions.required(() -> {
            Comment parent = requiredComment(commentId);
            Post post = requiredPostById(parent.postId(), PostQueryScope.STUDIO);
            return new ReplySeed(post.slug(), parent.id());
        }));
        return generate(seed.postSlug(), agentId, seed.parentCommentId());
    }

    @Override
    public CommentResult generateAutomatedAiComment(String postId, long postRevision, String agentId) {
        String slug = withDomainErrors(() -> transactions.required(() -> {
            Post post = requiredPostById(postId, PostQueryScope.PUBLISHED);
            if (post.revision() != postRevision || post.visibility() != PostVisibility.PUBLIC) {
                throw new BlogException(BlogErrorCode.VERSION_CONFLICT,
                        "自动评论任务对应的文章版本已经变化");
            }
            AgentProfile agent = requiredAgent(agentId);
            if (!agent.communityOwned()) {
                throw new BlogException(BlogErrorCode.INVALID_REQUEST,
                        "只有审核通过的会员 Agent 可以执行社区自动评论");
            }
            agent.ensureCanGenerate(PostVisibility.PUBLIC);
            return post.slug();
        }));
        return generate(slug, agentId, null);
    }

    @Override
    public PostAiSummaryResult getAiSummary(String postSlug) {
        return withDomainErrors(() -> transactions.required(() -> {
            Post post = requiredPost(postSlug, PostQueryScope.STUDIO);
            return summaryResult(post, summaries.findByPostId(post.id()).orElse(null));
        }));
    }

    @Override
    public PostAiSummaryResult generateAiSummary(String postSlug) {
        ensureSummaryAvailable();
        Post post = withDomainErrors(() -> transactions.required(
                () -> requiredPost(postSlug, PostQueryScope.STUDIO)));
        return summaryResult(post, ensureCurrentSummary(post));
    }

    @Override
    public AiSummaryCoverageResult getAiSummaryCoverage() {
        return withDomainErrors(() -> transactions.required(() -> coverage(null)));
    }

    @Override
    public AiSummaryCoverageResult backfillNextAiSummary() {
        ensureSummaryAvailable();
        PostSummary next = withDomainErrors(() -> transactions.required(this::nextPublicSummaryCandidate));
        if (next == null) {
            return getAiSummaryCoverage();
        }
        Post post = withDomainErrors(() -> transactions.required(
                () -> requiredPostById(next.id(), PostQueryScope.PUBLISHED)));
        ensureCurrentSummary(post);
        return withDomainErrors(() -> transactions.required(() -> coverage(post.slug())));
    }

    @Override
    public CommentResult publishComment(String commentId) {
        return withDomainErrors(() -> transactions.required(() -> {
            Comment current = requiredComment(commentId);
            Instant now = clock.instant();
            Comment published = current.parentCommentId() == null
                    ? current.publish(now)
                    : current.publishReplyTo(requiredComment(current.parentCommentId()), now);
            comments.save(current, published);
            return result(published);
        }));
    }

    @Override
    public CommentResult hideComment(String commentId) {
        return withDomainErrors(() -> transactions.required(() -> {
            Comment current = requiredComment(commentId);
            Instant now = clock.instant();
            // 一次审核操作覆盖整条后续对话；任意一条保存失败，事务会整体回滚。
            List<Comment> all = comments.findByPostId(current.postId(), CommentQueryScope.STUDIO);
            Set<String> idsToHide = descendantIds(current.id(), all);
            idsToHide.add(current.id());
            Comment hiddenRoot = current;
            for (Comment item : all) {
                if (idsToHide.contains(item.id()) && item.status() != CommentStatus.HIDDEN) {
                    Comment hidden = item.hide(now);
                    comments.save(item, hidden);
                    if (item.id().equals(current.id())) hiddenRoot = hidden;
                }
            }
            return result(hiddenRoot);
        }));
    }

    private CommentResult generate(String postSlug, String agentId, String parentCommentId) {
        if (!commentAi.isAvailable()) {
            throw new BlogException(BlogErrorCode.AI_UNAVAILABLE, "AI 评论服务尚未配置");
        }
        ensureSummaryAvailable();

        SummarySeed seed = withDomainErrors(() -> transactions.required(() -> {
            Post post = requiredPost(postSlug, PostQueryScope.STUDIO);
            AgentProfile agent = requiredAgent(agentId);
            agent.ensureCanGenerate(post.visibility());
            if (parentCommentId != null) {
                Comment parent = requiredComment(parentCommentId);
                parent.ensureCanReceiveAiReply(post.id(), agent.identity());
            }
            return new SummarySeed(post, agent.id());
        }));
        ensureCurrentSummary(seed.post());

        PreparedGeneration prepared = withDomainErrors(
                () -> transactions.required(() -> prepare(postSlug, seed.agentId(), parentCommentId)));
        try {
            // 模型请求发生在两个短事务之间，避免等待模型时一直占用数据库锁和连接。
            AiCommentGeneration generated = commentAi.generate(prepared.prompt());
            return withDomainErrors(() -> transactions.required(() -> complete(prepared, generated)));
        } catch (RuntimeException exception) {
            fail(prepared.run(), exception);
            if (exception instanceof BlogException blogException) {
                throw blogException;
            }
            throw new BlogException(BlogErrorCode.AI_GENERATION_FAILED,
                    parentCommentId == null ? "AI 评论生成失败，请稍后重试" : "AI 回复生成失败，请稍后重试",
                    exception);
        }
    }

    private PreparedGeneration prepare(String postSlug, String agentId, String parentCommentId) {
        Post post = requiredPost(postSlug, PostQueryScope.STUDIO);
        AgentProfile agent = requiredAgent(agentId);
        agent.ensureCanGenerate(post.visibility());
        PostAiSummary currentSummary = summaries.findByPostId(post.id())
                .filter(summary -> summary.isCurrentFor(post))
                .orElseThrow(() -> new BlogException(BlogErrorCode.VERSION_CONFLICT,
                        "文章摘要已过期，请重新生成评论"));

        List<Comment> existing = comments.findByPostId(post.id(), CommentQueryScope.STUDIO).stream()
                .filter(comment -> comment.status() != CommentStatus.HIDDEN)
                .toList();
        Comment parent = parentCommentId == null ? null : requiredComment(parentCommentId);
        if (parent != null) {
            parent.ensureCanReceiveAiReply(post.id(), agent.identity());
        }

        AgentRun run = parent == null
                ? AgentRun.start(
                        UUID.randomUUID().toString(), post.id(), post.revision(), agent.id(), agent.promptVersion(),
                        agent.model(), clock.instant())
                : AgentRun.startReply(
                        UUID.randomUUID().toString(), post.id(), post.revision(), agent.id(), agent.promptVersion(),
                        agent.model(), parent.id(), clock.instant());
        // 清理超时审计占位后再登记本次生成；唯一约束负责挡住相同版本的重复调用。
        runs.failStaleRunning(
                post.id(), post.revision(), agent.id(), parentCommentId,
                run.startedAt().minus(STALE_RUN_AFTER), run.startedAt());
        runs.add(run);

        AiCommentPrompt prompt = new AiCommentPrompt(
                agent.systemPrompt(), agent.model(), agent.temperature(), post.title(), post.description(), post.body(),
                post.visibility().name(), post.tags(), currentSummary.body(), relatedPosts(post, agent),
                existing.stream()
                        .map(comment -> new AiCommentPrompt.ExistingComment(
                                comment.id(), comment.parentCommentId(), comment.author().displayName(),
                                comment.body(), comment.createdAt()))
                        .toList(),
                parent == null ? null : new AiCommentPrompt.ReplyTarget(
                        parent.id(), parent.author().displayName(), parent.body()));
        return new PreparedGeneration(post, agent, parent, run, prompt);
    }

    private List<AiCommentPrompt.RelatedPost> relatedPosts(Post post, AgentProfile agent) {
        if (post.tags().isEmpty()) {
            return List.of();
        }
        PostQueryScope scope = post.visibility() == PostVisibility.PUBLIC
                ? PostQueryScope.PUBLISHED
                : PostQueryScope.STUDIO;
        Set<String> targetTags = normalizedTags(post.tags());
        List<RelatedCandidate> candidates = posts.findAll(scope).stream()
                .filter(candidate -> !candidate.id().equals(post.id()))
                .map(candidate -> new RelatedCandidate(candidate, sharedTags(candidate.tags(), targetTags)))
                .filter(candidate -> !candidate.sharedTags().isEmpty())
                .toList();
        Set<String> candidateIds = candidates.stream()
                .map(candidate -> candidate.post().id())
                .collect(Collectors.toSet());
        Map<String, PostAiSummary> summaryByPostId = summaries.findByPostIds(candidateIds).stream()
                .collect(Collectors.toMap(PostAiSummary::postId, summary -> summary));

        Comparator<RelatedCandidate> relevance = Comparator
                .comparingInt((RelatedCandidate candidate) -> candidate.sharedTags().size()).reversed()
                .thenComparing(candidate -> candidate.post().publishedAt(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.post().slugText());
        return candidates.stream()
                .filter(candidate -> agent.canProcessPrivate()
                        || candidate.post().visibility() == PostVisibility.PUBLIC)
                .filter(candidate -> {
                    PostAiSummary summary = summaryByPostId.get(candidate.post().id());
                    return summary != null && summary.postRevision() == candidate.post().revision();
                })
                .sorted(relevance)
                .limit(MAX_RELATED_POSTS)
                .sorted(Comparator.comparing(candidate -> candidate.post().publishedAt()))
                .map(candidate -> new AiCommentPrompt.RelatedPost(
                        candidate.post().title(), candidate.post().publishedAt(), candidate.sharedTags(),
                        summaryByPostId.get(candidate.post().id()).body()))
                .toList();
    }

    private PostAiSummary ensureCurrentSummary(Post post) {
        PostAiSummary current = withDomainErrors(() -> transactions.required(
                () -> summaries.findByPostId(post.id()).filter(summary -> summary.isCurrentFor(post)).orElse(null)));
        if (current != null) {
            return current;
        }

        AiSummaryGeneration generated;
        try {
            generated = summaryAi.generate(new AiSummaryPrompt(
                    post.title(), post.description(), post.tags(), post.body()));
        } catch (RuntimeException exception) {
            throw new BlogException(BlogErrorCode.AI_GENERATION_FAILED,
                    "AI 摘要生成失败，请稍后重试", exception);
        }
        Instant now = clock.instant();
        PostAiSummary summary = PostAiSummary.create(
                post.id(), post.revision(), generated.body(), generated.model(),
                generated.inputTokens(), generated.outputTokens(), now);
        return withDomainErrors(() -> transactions.required(() -> {
            Post latest = requiredPostById(post.id(), PostQueryScope.STUDIO);
            // 模型运行期间正文可能已变更，旧摘要不能覆盖当前版本的摘要。
            if (latest.revision() != post.revision()) {
                throw new BlogException(BlogErrorCode.VERSION_CONFLICT,
                        "摘要生成期间文章已更新，请重试");
            }
            summaries.save(summary);
            return summary;
        }));
    }

    private CommentResult complete(PreparedGeneration prepared, AiCommentGeneration generated) {
        Instant now = clock.instant();
        Comment comment = prepared.parent() == null
                ? Comment.createAiCandidate(
                        UUID.randomUUID().toString(), prepared.post().id(), prepared.agent().identity(),
                        generated.body(), now)
                : Comment.createAiReplyCandidate(
                        UUID.randomUUID().toString(), prepared.parent(), prepared.agent().identity(),
                        generated.body(), now);
        comments.add(comment);
        runs.save(prepared.run().succeed(comment.id(), generated.model(), generated.inputTokens(),
                generated.outputTokens(), now));
        return result(comment);
    }

    private void fail(AgentRun run, RuntimeException exception) {
        try {
            transactions.required(() -> {
                runs.save(run.fail(exception.getMessage(), clock.instant()));
                return null;
            });
        } catch (RuntimeException ignored) {
            exception.addSuppressed(ignored);
        }
    }

    private CommentListResult list(Post post, CommentQueryScope scope) {
        return new CommentListResult(comments.findByPostId(post.id(), scope).stream()
                .map(CommentApplicationService::result)
                .toList());
    }

    private AiSummaryCoverageResult coverage(String generatedSlug) {
        List<PostSummary> publicPosts = posts.findAll(PostQueryScope.PUBLISHED);
        Map<String, PostAiSummary> stored = summaries.findByPostIds(publicPosts.stream()
                        .map(PostSummary::id).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PostAiSummary::postId, summary -> summary));
        int current = 0;
        int missing = 0;
        int stale = 0;
        for (PostSummary post : publicPosts) {
            PostAiSummary summary = stored.get(post.id());
            if (summary == null) {
                missing++;
            } else if (summary.postRevision() == post.revision()) {
                current++;
            } else {
                stale++;
            }
        }
        return new AiSummaryCoverageResult(publicPosts.size(), current, missing, stale, generatedSlug);
    }

    private PostSummary nextPublicSummaryCandidate() {
        List<PostSummary> publicPosts = posts.findAll(PostQueryScope.PUBLISHED);
        Map<String, PostAiSummary> stored = summaries.findByPostIds(publicPosts.stream()
                        .map(PostSummary::id).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PostAiSummary::postId, summary -> summary));
        return publicPosts.stream()
                .filter(post -> {
                    PostAiSummary summary = stored.get(post.id());
                    return summary == null || summary.postRevision() != post.revision();
                })
                .min(Comparator.comparing(PostSummary::publishedAt).thenComparing(PostSummary::slugText))
                .orElse(null);
    }

    private PostAiSummaryResult summaryResult(Post post, PostAiSummary summary) {
        String state = summary == null ? "MISSING" : summary.isCurrentFor(post) ? "CURRENT" : "STALE";
        return new PostAiSummaryResult(
                post.slug(), post.revision(), summary == null ? null : summary.body(),
                summary == null ? null : summary.model(), state,
                summary == null ? null : summary.updatedAt());
    }

    private AgentProfile requiredAgent(String agentId) {
        return agents.findById(agentId)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "Agent 不存在"));
    }

    private Comment requiredComment(String commentId) {
        return comments.findById(commentId)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "评论不存在"));
    }

    private Post requiredPost(String slug, PostQueryScope scope) {
        return posts.findBySlug(slug, scope)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在"));
    }

    private Post requiredPostById(String postId, PostQueryScope scope) {
        return posts.findById(postId, scope)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在"));
    }

    private void ensureSummaryAvailable() {
        if (!summaryAi.isAvailable()) {
            throw new BlogException(BlogErrorCode.AI_UNAVAILABLE, "AI 摘要服务尚未配置");
        }
    }

    private static Set<String> normalizedTags(List<String> tags) {
        return tags.stream()
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static List<String> sharedTags(List<String> candidateTags, Set<String> targetTags) {
        return candidateTags.stream()
                .filter(tag -> targetTags.contains(tag.trim().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static Set<String> descendantIds(String rootId, List<Comment> comments) {
        Map<String, List<String>> children = new HashMap<>();
        for (Comment comment : comments) {
            if (comment.parentCommentId() != null) {
                children.computeIfAbsent(comment.parentCommentId(), ignored -> new ArrayList<>()).add(comment.id());
            }
        }
        Set<String> descendants = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(children.getOrDefault(rootId, List.of()));
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (descendants.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return descendants;
    }

    private static CommentResult result(Comment comment) {
        return new CommentResult(
                comment.id(), comment.parentCommentId(),
                new AuthorResult(
                        comment.author().id(), comment.author().username(), comment.author().displayName(),
                        comment.author().type().name(), comment.author().avatarUrl()),
                comment.body(), comment.status().name(), comment.createdAt(), comment.updatedAt());
    }

    private static <T> T withDomainErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (DomainException exception) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, exception.getMessage(), exception);
        }
    }

    private record SummarySeed(Post post, String agentId) {
    }

    private record ReplySeed(String postSlug, String parentCommentId) {
    }

    private record RelatedCandidate(PostSummary post, List<String> sharedTags) {
    }

    private record PreparedGeneration(
            Post post,
            AgentProfile agent,
            Comment parent,
            AgentRun run,
            AiCommentPrompt prompt
    ) {
    }
}
