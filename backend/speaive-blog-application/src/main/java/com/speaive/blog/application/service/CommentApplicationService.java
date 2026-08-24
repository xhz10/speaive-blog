package com.speaive.blog.application.service;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.application.port.out.ai.AiCommentGeneration;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiCommentPrompt;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.persistence.AgentRunRepository;
import com.speaive.blog.application.port.out.persistence.CommentQueryScope;
import com.speaive.blog.application.port.out.persistence.CommentRepository;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.comment.CommentListResult;
import com.speaive.blog.application.result.comment.CommentResult;
import com.speaive.blog.application.result.post.AuthorResult;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.agent.AgentRun;
import com.speaive.blog.domain.comment.Comment;
import com.speaive.blog.domain.comment.CommentStatus;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.Post;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CommentApplicationService implements CommentUseCase {
    private static final Duration STALE_RUN_AFTER = Duration.ofMinutes(15);

    private final PostRepository posts;
    private final AgentRepository agents;
    private final CommentRepository comments;
    private final AgentRunRepository runs;
    private final AiCommentGenerationPort ai;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CommentApplicationService(
            PostRepository posts,
            AgentRepository agents,
            CommentRepository comments,
            AgentRunRepository runs,
            AiCommentGenerationPort ai,
            TransactionRunner transactions,
            Clock clock) {
        this.posts = Objects.requireNonNull(posts, "posts");
        this.agents = Objects.requireNonNull(agents, "agents");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.ai = Objects.requireNonNull(ai, "ai");
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
        if (!ai.isAvailable()) {
            throw new BlogException(BlogErrorCode.AI_UNAVAILABLE, "AI 评论服务尚未配置");
        }

        PreparedGeneration prepared = withDomainErrors(() -> transactions.required(() -> prepare(postSlug, agentId)));
        try {
            AiCommentGeneration generated = ai.generate(prepared.prompt());
            return withDomainErrors(() -> transactions.required(() -> complete(prepared, generated)));
        } catch (RuntimeException exception) {
            fail(prepared.run(), exception);
            if (exception instanceof BlogException blogException) {
                throw blogException;
            }
            throw new BlogException(BlogErrorCode.AI_GENERATION_FAILED,
                    "AI 评论生成失败，请稍后重试", exception);
        }
    }

    @Override
    public CommentResult publishComment(String commentId) {
        return transition(commentId, true);
    }

    @Override
    public CommentResult hideComment(String commentId) {
        return transition(commentId, false);
    }

    private PreparedGeneration prepare(String postSlug, String agentId) {
        Post post = requiredPost(postSlug, PostQueryScope.STUDIO);
        AgentProfile agent = agents.findById(agentId)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "Agent 不存在"));
        agent.ensureCanGenerate(post.visibility());

        List<Comment> existing = comments.findByPostId(post.id(), CommentQueryScope.STUDIO).stream()
                .filter(comment -> comment.status() != CommentStatus.HIDDEN)
                .toList();
        AgentRun run = AgentRun.start(
                UUID.randomUUID().toString(), post.id(), post.revision(), agent.id(), agent.promptVersion(),
                agent.model(), clock.instant());
        runs.failStaleRunning(
                post.id(), post.revision(), agent.id(), run.startedAt().minus(STALE_RUN_AFTER), run.startedAt());
        runs.add(run);

        AiCommentPrompt prompt = new AiCommentPrompt(
                agent.systemPrompt(), agent.model(), agent.temperature(), post.title(), post.description(), post.body(),
                post.visibility().name(), existing.stream()
                        .map(comment -> new AiCommentPrompt.ExistingComment(
                                comment.author().displayName(), comment.body()))
                        .toList());
        return new PreparedGeneration(post, agent, run, prompt);
    }

    private CommentResult complete(PreparedGeneration prepared, AiCommentGeneration generated) {
        Instant now = clock.instant();
        Comment comment = Comment.createAiCandidate(
                UUID.randomUUID().toString(), prepared.post().id(), prepared.agent().identity(), generated.body(), now);
        comments.add(comment);
        runs.save(prepared.run().succeed(comment.id(), generated.model(), generated.inputTokens(),
                generated.outputTokens(), now));
        return result(comment);
    }

    private void fail(AgentRun run, RuntimeException exception) {
        try {
            transactions.required(() -> {
                String message = exception.getMessage();
                runs.save(run.fail(message, clock.instant()));
                return null;
            });
        } catch (RuntimeException ignored) {
            exception.addSuppressed(ignored);
        }
    }

    private CommentResult transition(String commentId, boolean publish) {
        return withDomainErrors(() -> transactions.required(() -> {
            Comment current = comments.findById(commentId)
                    .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "评论不存在"));
            Comment updated = publish ? current.publish(clock.instant()) : current.hide(clock.instant());
            comments.save(current, updated);
            return result(updated);
        }));
    }

    private CommentListResult list(Post post, CommentQueryScope scope) {
        return new CommentListResult(comments.findByPostId(post.id(), scope).stream()
                .map(CommentApplicationService::result)
                .toList());
    }

    private Post requiredPost(String slug, PostQueryScope scope) {
        return posts.findBySlug(slug, scope)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在"));
    }

    private static CommentResult result(Comment comment) {
        return new CommentResult(
                comment.id(),
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

    private record PreparedGeneration(
            Post post,
            AgentProfile agent,
            AgentRun run,
            AiCommentPrompt prompt
    ) {
    }
}
