package com.speaive.blog.application.service;

import com.speaive.blog.application.command.creative.CreateShareCommand;
import com.speaive.blog.application.command.creative.GenerateEditorialReviewCommand;
import com.speaive.blog.application.command.creative.InspirationTransitionCommand;
import com.speaive.blog.application.command.creative.InspirationWriteCommand;
import com.speaive.blog.application.command.creative.WorkCollectionWriteCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.creative.CreativeWorkspaceUseCase;
import com.speaive.blog.application.port.out.ai.AiCommentGeneration;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiCommentPrompt;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.persistence.CommentQueryScope;
import com.speaive.blog.application.port.out.persistence.CommentRepository;
import com.speaive.blog.application.port.out.persistence.ContentRevisionRepository;
import com.speaive.blog.application.port.out.persistence.CreativeWorkspaceRepository;
import com.speaive.blog.application.port.out.persistence.NovelFragmentQueryScope;
import com.speaive.blog.application.port.out.persistence.NovelFragmentRepository;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.creative.ContentRevisionListResult;
import com.speaive.blog.application.result.creative.ContentRevisionResult;
import com.speaive.blog.application.result.creative.DiscussionDigestResult;
import com.speaive.blog.application.result.creative.EditorialReviewListResult;
import com.speaive.blog.application.result.creative.EditorialReviewResult;
import com.speaive.blog.application.result.creative.InspirationListResult;
import com.speaive.blog.application.result.creative.InspirationResult;
import com.speaive.blog.application.result.creative.ResurfacingResult;
import com.speaive.blog.application.result.creative.ShareGrantListResult;
import com.speaive.blog.application.result.creative.ShareGrantResult;
import com.speaive.blog.application.result.creative.SharedContentResult;
import com.speaive.blog.application.result.creative.WorkCollectionListResult;
import com.speaive.blog.application.result.creative.WorkCollectionResult;
import com.speaive.blog.application.result.creative.WorkNavigationResult;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.comment.Comment;
import com.speaive.blog.domain.creative.ContentRevision;
import com.speaive.blog.domain.creative.CreativeContentType;
import com.speaive.blog.domain.creative.CreativeVisibility;
import com.speaive.blog.domain.creative.DiscussionDigest;
import com.speaive.blog.domain.creative.EditorialReview;
import com.speaive.blog.domain.creative.Inspiration;
import com.speaive.blog.domain.creative.InspirationKind;
import com.speaive.blog.domain.creative.InspirationStatus;
import com.speaive.blog.domain.creative.ShareGrant;
import com.speaive.blog.domain.creative.WorkCollection;
import com.speaive.blog.domain.creative.WorkItem;
import com.speaive.blog.domain.novel.NovelFragment;
import com.speaive.blog.domain.novel.NovelFragmentChange;
import com.speaive.blog.domain.novel.NovelFragmentContent;
import com.speaive.blog.domain.novel.NovelFragmentSummary;
import com.speaive.blog.domain.novel.NovelFragmentVisibility;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostChange;
import com.speaive.blog.domain.post.PostContent;
import com.speaive.blog.domain.post.PostSummary;
import com.speaive.blog.domain.post.PostVisibility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 创作工作区用例，当前汇集灵感、历史恢复、作品集、分享、私密编辑和圆桌摘要。职责跨度较大，后续应按业务能力渐进拆分，见架构评估文档。
 */
public final class CreativeWorkspaceApplicationService implements CreativeWorkspaceUseCase {
    private static final int MAX_SHARE_DAYS = 90;
    private static final int MAX_EDITORIAL_QUOTE = 4_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CreativeWorkspaceRepository workspace;
    private final ContentRevisionRepository revisions;
    private final PostRepository posts;
    private final NovelFragmentRepository novels;
    private final AgentRepository agents;
    private final CommentRepository comments;
    private final AiCommentGenerationPort ai;
    private final MarkdownPort markdown;
    private final TransactionRunner transactions;
    private final Clock clock;

    public CreativeWorkspaceApplicationService(
            CreativeWorkspaceRepository workspace,
            ContentRevisionRepository revisions,
            PostRepository posts,
            NovelFragmentRepository novels,
            AgentRepository agents,
            CommentRepository comments,
            AiCommentGenerationPort ai,
            MarkdownPort markdown,
            TransactionRunner transactions,
            Clock clock) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.posts = Objects.requireNonNull(posts, "posts");
        this.novels = Objects.requireNonNull(novels, "novels");
        this.agents = Objects.requireNonNull(agents, "agents");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.ai = Objects.requireNonNull(ai, "ai");
        this.markdown = Objects.requireNonNull(markdown, "markdown");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public InspirationListResult listInspirations() {
        return withErrors(() -> transactions.required(() -> new InspirationListResult(
                workspace.findInspirations().stream().map(CreativeWorkspaceApplicationService::result).toList())));
    }

    @Override
    public InspirationResult createInspiration(InspirationWriteCommand command) {
        return withErrors(() -> transactions.required(() -> {
            require(command, "灵感内容不能为空");
            String body = normalizeBody(command.body());
            Inspiration inspiration = Inspiration.create(
                    UUID.randomUUID().toString(), deriveTitle(command.title(), body), body,
                    parse(InspirationKind.class, command.kind(), "灵感类型不合法"), command.pinned(), clock.instant());
            workspace.addInspiration(inspiration);
            return result(inspiration);
        }));
    }

    @Override
    public InspirationResult updateInspiration(String id, long revision, InspirationWriteCommand command) {
        return withErrors(() -> transactions.required(() -> {
            require(command, "灵感内容不能为空");
            Inspiration current = requiredInspiration(id);
            String body = normalizeBody(command.body());
            Inspiration updated = current.edit(deriveTitle(command.title(), body), body,
                    parse(InspirationKind.class, command.kind(), "灵感类型不合法"), command.pinned(),
                    revision, clock.instant());
            workspace.saveInspiration(current, updated);
            return result(updated);
        }));
    }

    @Override
    public InspirationResult transitionInspiration(String id, InspirationTransitionCommand command) {
        return withErrors(() -> transactions.required(() -> {
            require(command, "灵感状态不能为空");
            Inspiration current = requiredInspiration(id);
            InspirationStatus status = parse(InspirationStatus.class, command.status(), "灵感状态不合法");
            CreativeContentType targetType = command.targetType() == null || command.targetType().isBlank()
                    ? null : contentType(command.targetType());
            if (targetType != null) ensureContentExists(targetType, command.targetSlug());
            Inspiration updated = current.transition(status, targetType, command.targetSlug(),
                    command.revision(), clock.instant());
            workspace.saveInspiration(current, updated);
            return result(updated);
        }));
    }

    @Override
    public ContentRevisionListResult listRevisions(String contentType, String slug) {
        return withErrors(() -> transactions.required(() -> revisionList(contentType(contentType), slug)));
    }

    @Override
    public ContentRevisionListResult restoreRevision(
            String contentType, String slug, long revision, String currentVersion) {
        return withErrors(() -> transactions.required(() -> {
            CreativeContentType type = contentType(contentType);
            ContentRevision historical = revisions.find(type, slug, revision)
                    .orElseThrow(() -> notFound("历史版本不存在"));
            Instant now = clock.instant();
            if (type == CreativeContentType.POST) {
                Post current = posts.findBySlug(slug, PostQueryScope.STUDIO)
                        .orElseThrow(() -> notFound("文章不存在"));
                PostContent restored = new PostContent(
                        historical.title(), historical.summary(), historical.publishedAt(), historical.tags(),
                        historical.cover(), historical.body());
                PostChange change = current.restore(restored,
                        parse(PostVisibility.class, historical.visibility(), "文章历史可见性不合法"),
                        currentVersion, now);
                posts.save(change, markdown.referencedMediaPaths(change.current().body(), change.current().cover()));
            } else {
                NovelFragment current = novels.findBySlug(slug, NovelFragmentQueryScope.STUDIO)
                        .orElseThrow(() -> notFound("小说片段不存在"));
                NovelFragmentChange change = current.restore(
                        new NovelFragmentContent(historical.title(), historical.summary(), historical.body()),
                        parse(NovelFragmentVisibility.class, historical.visibility(), "小说历史可见性不合法"),
                        currentVersion, now);
                novels.save(change);
            }
            return revisionList(type, slug);
        }));
    }

    @Override
    public WorkCollectionListResult listStudioWorks() {
        return withErrors(() -> transactions.required(() -> new WorkCollectionListResult(
                workspace.findWorks().stream().map(work -> workResult(work, false)).toList())));
    }

    @Override
    public WorkCollectionListResult listPublicWorks() {
        return withErrors(() -> transactions.required(() -> new WorkCollectionListResult(
                workspace.findWorks().stream()
                        .filter(work -> work.visibility() == CreativeVisibility.PUBLIC)
                        .map(work -> workResult(work, true))
                        .filter(work -> !work.items().isEmpty())
                        .toList())));
    }

    @Override
    public WorkCollectionResult getStudioWork(String slug) {
        return withErrors(() -> transactions.required(() -> workResult(requiredWork(slug), false)));
    }

    @Override
    public WorkCollectionResult getPublicWork(String slug) {
        return withErrors(() -> transactions.required(() -> {
            WorkCollection work = requiredWork(slug);
            if (work.visibility() != CreativeVisibility.PUBLIC) throw notFound("作品集不存在");
            WorkCollectionResult result = workResult(work, true);
            if (result.items().isEmpty()) throw notFound("作品集暂时没有公开内容");
            return result;
        }));
    }

    @Override
    public WorkCollectionResult createWork(WorkCollectionWriteCommand command) {
        return withErrors(() -> transactions.required(() -> {
            require(command, "作品集内容不能为空");
            List<WorkItem> items = workItems(command.items());
            items.forEach(item -> ensureContentExists(item.contentType(), item.contentSlug()));
            WorkCollection work = WorkCollection.create(
                    UUID.randomUUID().toString(), command.slug(), command.title(), command.description(),
                    command.cover(), visibility(command.visibility()), items, clock.instant());
            workspace.addWork(work);
            return workResult(work, false);
        }));
    }

    @Override
    public WorkCollectionResult updateWork(String slug, long revision, WorkCollectionWriteCommand command) {
        return withErrors(() -> transactions.required(() -> {
            require(command, "作品集内容不能为空");
            WorkCollection current = requiredWork(slug);
            if (command.slug() != null && !command.slug().isBlank() && !slug.equals(command.slug().trim())) {
                throw invalid("作品集 slug 创建后不能修改");
            }
            List<WorkItem> items = workItems(command.items());
            items.forEach(item -> ensureContentExists(item.contentType(), item.contentSlug()));
            WorkCollection updated = current.edit(command.title(), command.description(), command.cover(),
                    visibility(command.visibility()), items, revision, clock.instant());
            workspace.saveWork(current, updated);
            return workResult(updated, false);
        }));
    }

    @Override
    public WorkNavigationResult getPublicNavigation(String contentType, String contentSlug, String workSlug) {
        return withErrors(() -> transactions.required(() -> {
            CreativeContentType type = contentType(contentType);
            for (WorkCollection work : workspace.findWorks()) {
                if (work.visibility() != CreativeVisibility.PUBLIC) continue;
                if (workSlug != null && !workSlug.isBlank() && !work.slug().equals(workSlug.trim())) continue;
                WorkCollectionResult publicWork = workResult(work, true);
                for (int index = 0; index < publicWork.items().size(); index++) {
                    WorkCollectionResult.WorkItemResult item = publicWork.items().get(index);
                    if (item.contentType().equals(type.name()) && item.contentSlug().equals(contentSlug)) {
                        return new WorkNavigationResult(
                                work.slug(), work.title(),
                                index == 0 ? null : navigation(publicWork.items().get(index - 1)),
                                index + 1 >= publicWork.items().size()
                                        ? null : navigation(publicWork.items().get(index + 1)));
                    }
                }
            }
            return null;
        }));
    }

    @Override
    public ShareGrantListResult listShares(String contentType, String contentSlug) {
        return withErrors(() -> transactions.required(() -> {
            CreativeContentType type = contentType(contentType);
            ensureContentExists(type, contentSlug);
            Instant now = clock.instant();
            return new ShareGrantListResult(workspace.findShares(type, contentSlug).stream()
                    .map(grant -> shareResult(grant, null, now)).toList());
        }));
    }

    @Override
    public ShareGrantResult createShare(CreateShareCommand command) {
        return withErrors(() -> transactions.required(() -> {
            require(command, "分享内容不能为空");
            if (command.validDays() < 1 || command.validDays() > MAX_SHARE_DAYS) {
                throw invalid("分享有效期只能是 1 到 90 天");
            }
            CreativeContentType type = contentType(command.contentType());
            ensureContentExists(type, command.contentSlug());
            Instant now = clock.instant();
            String token = newToken();
            ShareGrant grant = ShareGrant.create(UUID.randomUUID().toString(), type, command.contentSlug(),
                    sha256(token), now.plus(Duration.ofDays(command.validDays())), now);
            workspace.addShare(grant);
            return shareResult(grant, token, now);
        }));
    }

    @Override
    public ShareGrantResult revokeShare(String id) {
        return withErrors(() -> transactions.required(() -> {
            ShareGrant current = workspace.findShareById(id).orElseThrow(() -> notFound("分享链接不存在"));
            ShareGrant revoked = current.revoke(clock.instant());
            workspace.saveShare(current, revoked);
            return shareResult(revoked, null, clock.instant());
        }));
    }

    @Override
    public SharedContentResult resolveShare(String token) {
        if (token == null || token.length() < 32 || token.length() > 100) throw notFound("分享链接不存在或已失效");
        return withErrors(() -> transactions.required(() -> {
            Instant now = clock.instant();
            ShareGrant grant = workspace.findShareByTokenHash(sha256(token))
                    .filter(value -> value.activeAt(now))
                    .orElseThrow(() -> notFound("分享链接不存在或已失效"));
            SharedContentResult result;
            if (grant.contentType() == CreativeContentType.POST) {
                Post post = posts.findBySlug(grant.contentSlug(), PostQueryScope.STUDIO)
                        .orElseThrow(() -> notFound("分享内容不存在"));
                result = new SharedContentResult(
                        "POST", post.slug(), post.title(), post.description(), post.body(), markdown.render(post.body()),
                        post.publishedAt(), post.updatedAt(), post.author().displayName(), grant.expiresAt());
            } else {
                NovelFragment novel = novels.findBySlug(grant.contentSlug(), NovelFragmentQueryScope.STUDIO)
                        .orElseThrow(() -> notFound("分享内容不存在"));
                result = new SharedContentResult(
                        "NOVEL", novel.slug(), novel.title(), novel.excerpt(), novel.body(), markdown.render(novel.body()),
                        novel.publishedAt(), novel.updatedAt(), novel.author().displayName(), grant.expiresAt());
            }
            workspace.touchShare(grant.id(), now);
            return result;
        }));
    }

    @Override
    public EditorialReviewListResult listEditorialReviews(String postSlug) {
        return withErrors(() -> transactions.required(() -> {
            Post post = requiredPost(postSlug, PostQueryScope.STUDIO);
            return new EditorialReviewListResult(workspace.findEditorialReviews(post.id()).stream()
                    .map(review -> editorialResult(review, post.revision())).toList());
        }));
    }

    @Override
    public EditorialReviewResult generateEditorialReview(
            String postSlug, GenerateEditorialReviewCommand command) {
        if (!ai.isAvailable()) throw new BlogException(BlogErrorCode.AI_UNAVAILABLE, "AI 编辑服务尚未配置");
        ReviewSeed seed = withErrors(() -> transactions.required(() -> {
            require(command, "编辑请求不能为空");
            Post post = requiredPost(postSlug, PostQueryScope.STUDIO);
            AgentProfile agent = agents.findById(command.agentId())
                    .orElseThrow(() -> notFound("Agent 不存在"));
            agent.ensureCanGenerate(post.visibility());
            return new ReviewSeed(post, agent, normalizeOptional(command.quoteText(), MAX_EDITORIAL_QUOTE),
                    normalizeOptional(command.quotePrefix(), 240), normalizeOptional(command.quoteSuffix(), 240));
        }));

        List<AiCommentPrompt.ExistingComment> focus = seed.quoteText() == null ? List.of() : List.of(
                new AiCommentPrompt.ExistingComment("selection", null, "作者选中的原文",
                        seed.quoteText(), clock.instant()));
        AiCommentPrompt prompt = new AiCommentPrompt(
                seed.agent().systemPrompt(), seed.agent().model(), Math.min(seed.agent().temperature(), 0.7),
                seed.post().title(), seed.post().description(), seed.post().body(), seed.post().visibility().name(),
                seed.post().tags(), "这是发布前的私密编辑，不生成公开评论。", List.of(), focus, null,
                seed.quoteText() == null
                        ? "以私密编辑身份审阅全文。先指出一个最值得修改的问题，再给出具体修改方向。只批评文本，不攻击作者；不要声称已经修改正文。"
                        : "重点审阅“作者选中的原文”。说明它在上下文中的问题、影响和可执行修改方向；必要时给出一版短改写。只批评文本，不攻击作者。");
        AiCommentGeneration generated = generate(prompt, "AI 编辑意见生成失败，请稍后重试");

        return withErrors(() -> transactions.required(() -> {
            Post current = requiredPost(postSlug, PostQueryScope.STUDIO);
            EditorialReview review = new EditorialReview(
                    UUID.randomUUID().toString(), seed.post().id(), seed.post().revision(), seed.agent().id(),
                    seed.quoteText(), seed.quotePrefix(), seed.quoteSuffix(), generated.body(), generated.model(),
                    generated.inputTokens(), generated.outputTokens(), clock.instant());
            workspace.addEditorialReview(review);
            return editorialResult(review, current.revision());
        }));
    }

    @Override
    public DiscussionDigestResult getStudioDiscussionDigest(String postSlug) {
        return discussionDigest(postSlug, PostQueryScope.STUDIO, CommentQueryScope.STUDIO, false);
    }

    @Override
    public DiscussionDigestResult getPublicDiscussionDigest(String postSlug) {
        return discussionDigest(postSlug, PostQueryScope.PUBLISHED, CommentQueryScope.PUBLISHED, true);
    }

    @Override
    public DiscussionDigestResult generateDiscussionDigest(String postSlug) {
        if (!ai.isAvailable()) throw new BlogException(BlogErrorCode.AI_UNAVAILABLE, "AI 圆桌摘要服务尚未配置");
        DigestSeed seed = withErrors(() -> transactions.required(() -> {
            Post post = requiredPost(postSlug, PostQueryScope.STUDIO);
            List<Comment> published = comments.findByPostId(post.id(), CommentQueryScope.PUBLISHED);
            if (published.size() < 2) throw invalid("至少需要两条已发布评论才能生成圆桌摘要");
            return new DigestSeed(post, published, fingerprint(published));
        }));

        List<AiCommentPrompt.ExistingComment> timeline = seed.comments().stream()
                .map(comment -> new AiCommentPrompt.ExistingComment(
                        comment.id(), comment.parentCommentId(), comment.author().displayName(),
                        comment.body(), comment.createdAt()))
                .toList();
        AiCommentPrompt prompt = new AiCommentPrompt(
                "你是克制、准确的圆桌主持人。必须先理解各角色的真实观点，不得编造共识或分歧。",
                null, 0.2, seed.post().title(), seed.post().description(), seed.post().body(),
                seed.post().visibility().name(), seed.post().tags(), "请只依据文章和已发布评论。",
                List.of(), timeline, null,
                "把这场 Agent 讨论压缩成 120 到 360 个中文字符。固定使用三段：共识：……；分歧：……；未决问题：……。没有明显共识时应明确说没有，不要硬凑。只输出摘要正文。");
        AiCommentGeneration generated = generate(prompt, "AI 圆桌摘要生成失败，请稍后重试");

        return withErrors(() -> transactions.required(() -> {
            Post current = requiredPost(postSlug, PostQueryScope.STUDIO);
            List<Comment> published = comments.findByPostId(current.id(), CommentQueryScope.PUBLISHED);
            String currentFingerprint = fingerprint(published);
            if (current.revision() != seed.post().revision() || !currentFingerprint.equals(seed.fingerprint())) {
                throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "文章或评论已发生变化，请重新生成圆桌摘要");
            }
            DiscussionDigest digest = new DiscussionDigest(
                    current.id(), current.revision(), currentFingerprint, generated.body(), generated.model(),
                    generated.inputTokens(), generated.outputTokens(), clock.instant());
            workspace.saveDiscussionDigest(digest);
            return digestResult(current.slug(), digest, "CURRENT", published.size());
        }));
    }

    @Override
    public ResurfacingResult getResurfacingSuggestions() {
        return withErrors(() -> transactions.required(() -> {
            List<ResurfacingResult.ResurfacingItem> result = new ArrayList<>();
            List<PostSummary> postItems = posts.findAll(PostQueryScope.STUDIO);
            Map<String, List<PostSummary>> byTag = new LinkedHashMap<>();
            postItems.forEach(post -> post.tags().forEach(tag -> byTag
                    .computeIfAbsent(tag.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(post)));
            byTag.entrySet().stream()
                    .filter(entry -> entry.getValue().size() >= 2)
                    .sorted(Comparator.<Map.Entry<String, List<PostSummary>>>comparingInt(entry -> entry.getValue().size())
                            .reversed().thenComparing(Map.Entry::getKey))
                    .limit(3)
                    .forEach(entry -> {
                        List<PostSummary> related = entry.getValue().stream()
                                .sorted(Comparator.comparing(PostSummary::publishedAt)).toList();
                        result.add(new ResurfacingResult.ResurfacingItem(
                                "THEME", "再次看看 #" + entry.getKey(),
                                related.size() + " 篇文章记录了这个主题的变化，可以整理成作品集。",
                                "/archive/?tag=" + entry.getKey(), List.of(entry.getKey()),
                                related.stream().map(PostSummary::slugText).toList()));
                    });

            Instant staleBefore = clock.instant().minus(Duration.ofDays(30));
            novels.findAll(NovelFragmentQueryScope.STUDIO).stream()
                    .filter(novel -> novel.updatedAt().isBefore(staleBefore))
                    .sorted(Comparator.comparing(NovelFragmentSummary::updatedAt))
                    .limit(4)
                    .forEach(novel -> result.add(new ResurfacingResult.ResurfacingItem(
                            "UNFINISHED", "这个故事还在等你：" + novel.title(),
                            "已经一段时间没有续写。哪怕只补一句，也算让它继续呼吸。",
                            "/studio/novels/edit/" + novel.slugText() + "/", List.of(), List.of(novel.slugText()))));

            postItems.stream()
                    .filter(post -> post.updatedAt().isBefore(clock.instant().minus(Duration.ofDays(180))))
                    .sorted(Comparator.comparing(PostSummary::updatedAt))
                    .limit(3)
                    .forEach(post -> result.add(new ResurfacingResult.ResurfacingItem(
                            "OLD_POST", "旧文章回访：" + post.title(),
                            "现在的你还同意当时的判断吗？可以补一段后记，或者把它放进新的阅读路径。",
                            "/studio/edit/" + post.slugText() + "/", post.tags(), List.of(post.slugText()))));
            return new ResurfacingResult(result.stream().limit(10).toList());
        }));
    }

    private DiscussionDigestResult discussionDigest(
            String postSlug, PostQueryScope postScope, CommentQueryScope commentScope, boolean hideStaleBody) {
        return withErrors(() -> transactions.required(() -> {
            Post post = requiredPost(postSlug, postScope);
            List<Comment> published = comments.findByPostId(post.id(), commentScope);
            DiscussionDigest digest = workspace.findDiscussionDigest(post.id()).orElse(null);
            if (digest == null) return new DiscussionDigestResult(post.slug(), null, null, "MISSING", published.size(), null);
            String state = digest.postRevision() == post.revision()
                    && digest.commentsFingerprint().equals(fingerprint(published)) ? "CURRENT" : "STALE";
            return new DiscussionDigestResult(post.slug(), hideStaleBody && !state.equals("CURRENT") ? null : digest.body(),
                    digest.model(), state, published.size(), digest.updatedAt());
        }));
    }

    private ContentRevisionListResult revisionList(CreativeContentType type, String slug) {
        ensureContentExists(type, slug);
        return new ContentRevisionListResult(revisions.findAll(type, slug).stream()
                .map(CreativeWorkspaceApplicationService::revisionResult).toList());
    }

    private WorkCollectionResult workResult(WorkCollection work, boolean publicOnly) {
        List<WorkCollectionResult.WorkItemResult> items = work.items().stream()
                .map(item -> resolveWorkItem(item, publicOnly))
                .flatMap(Optional::stream)
                .toList();
        return new WorkCollectionResult(work.slug(), work.title(), work.description(), blankToNull(work.cover()),
                work.visibility().name(), work.revision(), work.updatedAt(), items);
    }

    private Optional<WorkCollectionResult.WorkItemResult> resolveWorkItem(WorkItem item, boolean publicOnly) {
        if (item.contentType() == CreativeContentType.POST) {
            return posts.findBySlug(item.contentSlug(), publicOnly ? PostQueryScope.PUBLISHED : PostQueryScope.STUDIO)
                    .map(post -> new WorkCollectionResult.WorkItemResult(
                            "POST", post.slug(), item.position(), post.title(), post.description(),
                            "/blog/" + post.slug() + "/", post.publishedAt()));
        }
        return novels.findBySlug(item.contentSlug(),
                        publicOnly ? NovelFragmentQueryScope.PUBLISHED : NovelFragmentQueryScope.STUDIO)
                .map(novel -> new WorkCollectionResult.WorkItemResult(
                        "NOVEL", novel.slug(), item.position(), novel.title(), novel.excerpt(),
                        "/novels/" + novel.slug() + "/", novel.publishedAt()));
    }

    private EditorialReviewResult editorialResult(EditorialReview review, long currentRevision) {
        String displayName = agents.findById(review.agentId())
                .map(agent -> agent.identity().displayName()).orElse("已移除的 Agent");
        return new EditorialReviewResult(
                review.id(), review.postRevision(), review.agentId(), displayName,
                review.quoteText(), review.quotePrefix(), review.quoteSuffix(), review.body(), review.model(),
                review.createdAt(), review.postRevision() != currentRevision);
    }

    private void ensureContentExists(CreativeContentType type, String slug) {
        if (slug == null || slug.isBlank()) throw invalid("内容 slug 不能为空");
        boolean exists = type == CreativeContentType.POST
                ? posts.findBySlug(slug.trim(), PostQueryScope.STUDIO).isPresent()
                : novels.findBySlug(slug.trim(), NovelFragmentQueryScope.STUDIO).isPresent();
        if (!exists) throw notFound(type == CreativeContentType.POST ? "文章不存在" : "小说片段不存在");
    }

    private Post requiredPost(String slug, PostQueryScope scope) {
        return posts.findBySlug(slug, scope).orElseThrow(() -> notFound("文章不存在"));
    }

    private Inspiration requiredInspiration(String id) {
        return workspace.findInspirationById(id).orElseThrow(() -> notFound("灵感不存在"));
    }

    private WorkCollection requiredWork(String slug) {
        return workspace.findWorkBySlug(slug).orElseThrow(() -> notFound("作品集不存在"));
    }

    private AiCommentGeneration generate(AiCommentPrompt prompt, String failureMessage) {
        try {
            return ai.generate(prompt);
        } catch (RuntimeException exception) {
            if (exception instanceof BlogException blogException) throw blogException;
            throw new BlogException(BlogErrorCode.AI_GENERATION_FAILED, failureMessage, exception);
        }
    }

    private static WorkNavigationResult.NavigationItem navigation(WorkCollectionResult.WorkItemResult item) {
        return new WorkNavigationResult.NavigationItem(
                item.contentType(), item.contentSlug(), item.title(), item.href());
    }

    private static List<WorkItem> workItems(List<WorkCollectionWriteCommand.WorkItemCommand> source) {
        List<WorkCollectionWriteCommand.WorkItemCommand> commands = source == null ? List.of() : source;
        List<WorkItem> result = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            WorkCollectionWriteCommand.WorkItemCommand item = commands.get(index);
            result.add(new WorkItem(contentType(item.contentType()), item.contentSlug(), index));
        }
        return List.copyOf(result);
    }

    private static InspirationResult result(Inspiration value) {
        return new InspirationResult(
                value.id(), value.title(), value.body(), value.kind().name(), value.status().name(), value.pinned(),
                value.targetType() == null ? null : value.targetType().name(), value.targetSlug(), value.revision(),
                value.createdAt(), value.updatedAt());
    }

    private static ContentRevisionResult revisionResult(ContentRevision value) {
        return new ContentRevisionResult(
                value.contentType().name(), value.revision(), value.eventType(), value.slug(), value.title(),
                value.summary(), value.body(), value.publishedAt(), value.tags(), value.cover(), value.status(),
                value.visibility(), value.updatedAt(), value.recordedAt());
    }

    private static ShareGrantResult shareResult(ShareGrant grant, String token, Instant now) {
        return new ShareGrantResult(
                grant.id(), grant.contentType().name(), grant.contentSlug(), token, grant.expiresAt(),
                grant.revokedAt(), grant.lastAccessedAt(), grant.createdAt(), grant.activeAt(now));
    }

    private static DiscussionDigestResult digestResult(
            String slug, DiscussionDigest digest, String state, int commentCount) {
        return new DiscussionDigestResult(
                slug, digest.body(), digest.model(), state, commentCount, digest.updatedAt());
    }

    private static CreativeContentType contentType(String value) {
        return parse(CreativeContentType.class, value, "内容类型只能是 POST 或 NOVEL");
    }

    private static CreativeVisibility visibility(String value) {
        return parse(CreativeVisibility.class, value, "作品集可见性只能是 PUBLIC 或 ADMIN_ONLY");
    }

    private static <T extends Enum<T>> T parse(Class<T> type, String value, String message) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, message, exception);
        }
    }

    private static String deriveTitle(String title, String body) {
        if (title != null && !title.isBlank()) return title.trim();
        String firstLine = body.lines().map(String::trim).filter(line -> !line.isEmpty()).findFirst().orElse("未命名灵感");
        int codePoints = firstLine.codePointCount(0, firstLine.length());
        return codePoints <= 36 ? firstLine : firstLine.substring(0, firstLine.offsetByCodePoints(0, 36)) + "…";
    }

    private static String normalizeBody(String body) {
        String result = body == null ? "" : body.trim();
        if (result.isEmpty()) throw invalid("灵感内容不能为空");
        return result;
    }

    private static String normalizeOptional(String value, int maxCodePoints) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.codePointCount(0, result.length()) > maxCodePoints) {
            throw invalid("选中文本超过长度限制");
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String fingerprint(List<Comment> comments) {
        StringBuilder value = new StringBuilder();
        comments.stream().sorted(Comparator.comparing(Comment::createdAt).thenComparing(Comment::id))
                .forEach(comment -> value.append(comment.id()).append('|')
                        .append(comment.parentCommentId()).append('|')
                        .append(comment.updatedAt()).append('|')
                        .append(comment.body()).append('\n'));
        return sha256(value.toString());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private static void require(Object value, String message) {
        if (value == null) throw invalid(message);
    }

    private static BlogException invalid(String message) {
        return new BlogException(BlogErrorCode.INVALID_REQUEST, message);
    }

    private static BlogException notFound(String message) {
        return new BlogException(BlogErrorCode.NOT_FOUND, message);
    }

    private static <T> T withErrors(java.util.function.Supplier<T> action) {
        return PostApplicationSupport.withDomainErrors(action);
    }

    private record ReviewSeed(
            Post post, AgentProfile agent, String quoteText, String quotePrefix, String quoteSuffix) {
    }

    private record DigestSeed(Post post, List<Comment> comments, String fingerprint) {
        private DigestSeed {
            comments = List.copyOf(comments);
        }
    }
}
