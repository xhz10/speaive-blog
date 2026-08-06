package com.speaive.blog.application;

import com.speaive.blog.application.port.in.BlogUseCase;
import com.speaive.blog.application.port.in.MarkdownInboxUseCase;
import com.speaive.blog.application.port.out.AuthorRepository;
import com.speaive.blog.application.port.out.MarkdownImportLedger;
import com.speaive.blog.application.port.out.MarkdownPort;
import com.speaive.blog.application.port.out.MediaStoragePort;
import com.speaive.blog.application.port.out.PostQueryScope;
import com.speaive.blog.application.port.out.PostRepository;
import com.speaive.blog.application.port.out.TransactionRunner;
import com.speaive.blog.application.result.ArchivedPostResult;
import com.speaive.blog.application.result.AuthorResult;
import com.speaive.blog.application.result.MarkdownImportOutcome;
import com.speaive.blog.application.result.MediaContentResult;
import com.speaive.blog.application.result.PostDetailResult;
import com.speaive.blog.application.result.PostListResult;
import com.speaive.blog.application.result.PostSummaryResult;
import com.speaive.blog.application.result.StoredMediaResult;
import com.speaive.blog.application.support.MarkdownParseRequest;
import com.speaive.blog.application.support.ParsedPostDocument;
import com.speaive.blog.domain.ArchivedPost;
import com.speaive.blog.domain.Author;
import com.speaive.blog.domain.DomainErrorCode;
import com.speaive.blog.domain.DomainException;
import com.speaive.blog.domain.MediaContent;
import com.speaive.blog.domain.Post;
import com.speaive.blog.domain.PostChange;
import com.speaive.blog.domain.PostContent;
import com.speaive.blog.domain.PostRevisionEventType;
import com.speaive.blog.domain.PostSlug;
import com.speaive.blog.domain.PostSummary;
import com.speaive.blog.domain.StoredMedia;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class BlogApplicationService implements BlogUseCase, MarkdownInboxUseCase {
    private static final String CONTENT_HASH_ALGORITHM = "SHA-256";

    private final PostRepository posts;
    private final AuthorRepository authors;
    private final MarkdownPort markdown;
    private final MediaStoragePort media;
    private final MarkdownImportLedger importLedger;
    private final TransactionRunner transactions;
    private final Clock clock;

    public BlogApplicationService(
            PostRepository posts,
            AuthorRepository authors,
            MarkdownPort markdown,
            MediaStoragePort media,
            MarkdownImportLedger importLedger,
            TransactionRunner transactions,
            Clock clock) {
        this.posts = Objects.requireNonNull(posts, "posts");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.markdown = Objects.requireNonNull(markdown, "markdown");
        this.media = Objects.requireNonNull(media, "media");
        this.importLedger = Objects.requireNonNull(importLedger, "importLedger");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PostListResult listStudioPosts() {
        return withDomainErrors(() -> transactions.required(() -> list(PostQueryScope.STUDIO)));
    }

    @Override
    public PostListResult listPublishedPosts() {
        return withDomainErrors(() -> transactions.required(() -> list(PostQueryScope.PUBLISHED)));
    }

    @Override
    public PostDetailResult getStudioPost(String slug) {
        return withDomainErrors(() -> transactions.required(
                () -> detail(requiredPost(slug, PostQueryScope.STUDIO))));
    }

    @Override
    public PostDetailResult getPublishedPost(String slug) {
        return withDomainErrors(() -> transactions.required(
                () -> detail(requiredPost(slug, PostQueryScope.PUBLISHED))));
    }

    @Override
    public PostDetailResult createDraft(PostWriteCommand command) {
        return withDomainErrors(() -> transactions.required(() -> detail(create(command, PostRevisionEventType.CREATE))));
    }

    @Override
    public PostDetailResult update(String slug, String version, PostWriteCommand command) {
        return withDomainErrors(() -> transactions.required(() -> {
            Post current = lockedPost(slug);
            current.assertVersion(version);
            Instant publishedAt = command.publishedAt() == null ? current.publishedAt() : command.publishedAt();
            ParsedPostDocument normalized = markdown.normalize(command, current.slug(), publishedAt);
            PostChange change = current.update(content(normalized), version, clock.instant());
            posts.save(change);
            return detail(change.current());
        }));
    }

    @Override
    public PostDetailResult publish(String slug, String version) {
        return transition(slug, version, Transition.PUBLISH);
    }

    @Override
    public PostDetailResult unpublish(String slug, String version) {
        return transition(slug, version, Transition.UNPUBLISH);
    }

    @Override
    public ArchivedPostResult archive(String slug, String version) {
        return withDomainErrors(() -> transactions.required(() -> {
            Post current = lockedPost(slug);
            ArchivedPost archived = posts.archive(current.archive(version, clock.instant()));
            return new ArchivedPostResult(
                    archived.slug(),
                    archived.archivedFrom().name(),
                    archived.archiveFileName()
            );
        }));
    }

    @Override
    public PostDetailResult importDraft(String fileName, byte[] source) {
        return withDomainErrors(() -> transactions.required(() -> detail(importPost(fileName, source))));
    }

    @Override
    public MarkdownImportOutcome importOnce(String fileName, byte[] source) {
        return withDomainErrors(() -> transactions.required(() -> {
            String sha256 = sha256(source);
            importLedger.lockForImport(sha256);
            if (importLedger.contains(sha256)) {
                return MarkdownImportOutcome.ALREADY_IMPORTED;
            }
            Post imported = importPost(fileName, source);
            importLedger.record(sha256, fileName, imported.slug(), clock.instant());
            return MarkdownImportOutcome.IMPORTED;
        }));
    }

    @Override
    public StoredMediaResult storeMedia(String fileName, String mimeType, byte[] bytes) {
        StoredMedia stored = media.store(fileName, mimeType, bytes);
        return new StoredMediaResult(stored.url(), stored.relativePath(), stored.mimeType(), stored.size());
    }

    @Override
    public MediaContentResult readMedia(String path) {
        MediaContent content = media.read(path);
        return new MediaContentResult(content.mimeType(), content.bytes());
    }

    @Override
    public String preview(String source) {
        return markdown.render(source);
    }

    private PostListResult list(PostQueryScope scope) {
        List<PostSummaryResult> items = posts.findAll(scope).stream().map(this::summary).toList();
        return new PostListResult(items, List.of());
    }

    private Post create(PostWriteCommand command, PostRevisionEventType eventType) {
        if (command == null) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "文章内容不能为空");
        }
        Instant now = clock.instant();
        Instant publishedAt = command.publishedAt() == null ? now : command.publishedAt();
        PostSlug slug = PostSlug.of(command.slug());
        ParsedPostDocument normalized = markdown.normalize(command, slug.value(), publishedAt);
        Author author = requiredContentAuthor();
        Post post = Post.createDraft(UUID.randomUUID().toString(), slug, content(normalized), author, now);
        posts.add(post, eventType);
        return post;
    }

    private Post importPost(String fileName, byte[] source) {
        Instant now = clock.instant();
        ParsedPostDocument parsed = markdown.parse(new MarkdownParseRequest(fileName, source, now));
        Author author = requiredContentAuthor();
        Post post = Post.createDraft(
                UUID.randomUUID().toString(),
                PostSlug.of(parsed.slug()),
                content(parsed),
                author,
                now
        );
        posts.add(post, PostRevisionEventType.IMPORT);
        return post;
    }

    private PostDetailResult transition(String slug, String version, Transition transition) {
        return withDomainErrors(() -> transactions.required(() -> {
            Post current = lockedPost(slug);
            PostChange change = switch (transition) {
                case PUBLISH -> current.publish(version, clock.instant());
                case UNPUBLISH -> current.unpublish(version, clock.instant());
            };
            posts.save(change);
            return detail(change.current());
        }));
    }

    private Post requiredPost(String slug, PostQueryScope scope) {
        return withDomainErrors(() -> posts.findBySlug(PostSlug.of(slug).value(), scope)
                .orElseThrow(BlogApplicationService::notFound));
    }

    private Post lockedPost(String slug) {
        return posts.lockBySlug(PostSlug.of(slug).value()).orElseThrow(BlogApplicationService::notFound);
    }

    private Author requiredContentAuthor() {
        Author author = authors.findById(Author.ADMIN_ID)
                .orElseThrow(() -> new BlogException(BlogErrorCode.STORAGE_ERROR, "固定管理员作者不存在"));
        if (!author.canAuthor()) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "固定管理员作者已禁用");
        }
        return author;
    }

    private PostContent content(ParsedPostDocument document) {
        return new PostContent(
                document.title(),
                document.description(),
                document.publishedAt(),
                document.tags(),
                document.cover(),
                document.body()
        );
    }

    private PostDetailResult detail(Post post) {
        return new PostDetailResult(
                post.slug(),
                post.title(),
                post.description(),
                post.publishedAt(),
                post.updatedAt(),
                post.tags(),
                post.cover(),
                author(post.author()),
                post.status().name(),
                post.body(),
                markdown.render(post.body()),
                post.version()
        );
    }

    private PostSummaryResult summary(PostSummary post) {
        return new PostSummaryResult(
                post.slugText(),
                post.title(),
                post.description(),
                post.publishedAt(),
                post.updatedAt(),
                post.tags(),
                post.cover(),
                author(post.author()),
                post.status().name(),
                post.version()
        );
    }

    private static AuthorResult author(Author author) {
        return new AuthorResult(
                author.id(),
                author.username(),
                author.displayName(),
                author.type().name(),
                author.avatarUrl()
        );
    }

    private static String sha256(byte[] source) {
        Objects.requireNonNull(source, "source");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(CONTENT_HASH_ALGORITHM).digest(source));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 " + CONTENT_HASH_ALGORITHM, exception);
        }
    }

    private static BlogException notFound() {
        return new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在");
    }

    private static <T> T withDomainErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (DomainException exception) {
            throw new BlogException(toApplicationCode(exception.code()), exception.getMessage(), exception);
        }
    }

    private static BlogErrorCode toApplicationCode(DomainErrorCode code) {
        return switch (code) {
            case INVALID_SLUG -> BlogErrorCode.INVALID_FILE_NAME;
            case INVALID_CONTENT -> BlogErrorCode.INVALID_MARKDOWN;
            case INVALID_STATE, INVALID_AUTHOR -> BlogErrorCode.INVALID_REQUEST;
            case VERSION_CONFLICT -> BlogErrorCode.VERSION_CONFLICT;
        };
    }

    private enum Transition {
        PUBLISH,
        UNPUBLISH
    }
}
