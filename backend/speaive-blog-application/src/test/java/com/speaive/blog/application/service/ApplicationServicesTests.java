package com.speaive.blog.application.service;

import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.importing.MarkdownInboxUseCase;
import com.speaive.blog.application.port.in.markdown.MarkdownUseCase;
import com.speaive.blog.application.port.in.media.MediaUseCase;
import com.speaive.blog.application.port.in.post.PostUseCase;
import com.speaive.blog.application.port.out.importing.MarkdownImportLedger;
import com.speaive.blog.application.port.out.markdown.MarkdownParseRequest;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.markdown.ParsedPostDocument;
import com.speaive.blog.application.port.out.media.MediaContent;
import com.speaive.blog.application.port.out.media.MediaReadScope;
import com.speaive.blog.application.port.out.media.MediaStoragePort;
import com.speaive.blog.application.port.out.media.StoredMedia;
import com.speaive.blog.application.port.out.persistence.ArchiveReceipt;
import com.speaive.blog.application.port.out.persistence.AuthorRepository;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.importing.MarkdownImportOutcome;
import com.speaive.blog.application.result.media.MediaContentResult;
import com.speaive.blog.application.result.media.StoredMediaResult;
import com.speaive.blog.application.result.post.ArchivedPostResult;
import com.speaive.blog.application.result.post.AuthorResult;
import com.speaive.blog.application.result.post.ContentScanErrorResult;
import com.speaive.blog.application.result.post.PostDetailResult;
import com.speaive.blog.application.result.post.PostListResult;
import com.speaive.blog.application.result.post.PostSummaryResult;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostChange;
import com.speaive.blog.domain.post.PostContent;
import com.speaive.blog.domain.post.PostRevisionEventType;
import com.speaive.blog.domain.post.PostStatus;
import com.speaive.blog.domain.post.PostSummary;
import com.speaive.blog.domain.post.PostVisibility;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationServicesTests {
    private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");
    private static final Instant CREATED_AT = NOW.minusSeconds(60);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createAndImportBindTheFixedAdminAndRecordTheirOwnEvents() {
        Fixture fixture = new Fixture();

        PostDetailResult created = fixture.postsService.createDraft(command("created-post", "网页创建", null));
        PostDetailResult imported = fixture.markdownService.importDraft(
                "imported-post.md", "# 导入正文".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(PostRevisionEventType.CREATE, PostRevisionEventType.IMPORT),
                fixture.posts.added.stream().map(AddedPost::eventType).toList());
        assertEquals(2, fixture.posts.added.size());
        assertTrue(fixture.posts.added.stream().allMatch(added -> added.post().author().equals(Author.ADMIN)));
        assertEquals(List.of(Author.ADMIN_ID, Author.ADMIN_ID), fixture.authors.requestedIds);
        assertEquals(Author.ADMIN_ID, created.author().id());
        assertEquals(Author.ADMIN_ID, imported.author().id());
        assertEquals("DRAFT", created.status());
        assertEquals("DRAFT", imported.status());
        assertEquals("ADMIN_ONLY", created.visibility());
        assertEquals("ADMIN_ONLY", imported.visibility());
        assertEquals(NOW, created.publishedAt());
        assertEquals(2, fixture.transactions.calls);
        assertTrue(fixture.transactions.outsideTransactionCalls.isEmpty());
    }

    @Test
    void createDraftKeepsCommandSlugWhenMarkdownNormalizerReturnsAnotherSlug() {
        Fixture fixture = new Fixture();
        fixture.markdown.normalizedSlug = "adapter-rewritten-slug";

        PostDetailResult created = fixture.postsService.createDraft(command("requested-slug", "网页创建", null));

        assertEquals("requested-slug", created.slug());
        assertEquals("requested-slug", fixture.posts.added.getFirst().post().slug());
    }

    @Test
    void disabledFixedAdminIsReportedAsRetryableStorageFailure() {
        Fixture fixture = new Fixture();
        fixture.authors.author = new Author(
                Author.ADMIN_ID, "admin", "Speaive", Author.ADMIN.type(), null, AuthorStatus.DISABLED);

        BlogException exception = assertThrows(BlogException.class, () -> fixture.markdownService.importOnce(
                "disabled-admin.md", "# Valid Markdown".getBytes(StandardCharsets.UTF_8)));

        assertSame(BlogErrorCode.STORAGE_ERROR, exception.code());
        assertTrue(fixture.posts.added.isEmpty());
        assertTrue(fixture.ledger.records.isEmpty());
    }

    @Test
    void updatePublishUnpublishAndArchivePersistAggregateChanges() {
        Fixture fixture = new Fixture();
        Post original = originalPost("workflow-id", "workflow");
        fixture.posts.current = original;

        PostDetailResult updated = fixture.postsService.update(
                original.slug(), original.version(), command(original.slug(), "更新正文", null));
        PostDetailResult published = fixture.postsService.publish(original.slug(), updated.version());
        PostDetailResult unpublished = fixture.postsService.unpublish(original.slug(), published.version());
        ArchivedPostResult archived = fixture.postsService.archive(original.slug(), unpublished.version());

        assertEquals(List.of(
                        PostRevisionEventType.UPDATE,
                        PostRevisionEventType.PUBLISH,
                        PostRevisionEventType.UNPUBLISH),
                fixture.posts.saved.stream().map(PostChange::eventType).toList());
        assertEquals(1, fixture.posts.archived.size());
        assertEquals(PostRevisionEventType.ARCHIVE, fixture.posts.archived.getFirst().eventType());

        PostChange update = fixture.posts.saved.get(0);
        PostChange publish = fixture.posts.saved.get(1);
        PostChange unpublish = fixture.posts.saved.get(2);
        PostChange archive = fixture.posts.archived.getFirst();
        assertEquals(original, update.previous());
        assertEquals(update.current(), publish.previous());
        assertEquals(publish.current(), unpublish.previous());
        assertEquals(unpublish.current(), archive.previous());
        assertEquals(List.of(2L, 3L, 4L, 5L), List.of(
                update.current().revision(),
                publish.current().revision(),
                unpublish.current().revision(),
                archive.current().revision()));
        assertTrue(List.of(update, publish, unpublish, archive).stream()
                .allMatch(change -> change.current().author().equals(Author.ADMIN)));

        assertEquals("更新正文", updated.title());
        assertEquals("PUBLISHED", published.status());
        assertEquals("DRAFT", unpublished.status());
        assertEquals("workflow", archived.slug());
        assertEquals("DRAFT", archived.archivedFrom());
        assertEquals("fake:workflow-id:5", archived.archiveReference());
        assertEquals(4, fixture.transactions.calls);
        assertTrue(fixture.transactions.outsideTransactionCalls.isEmpty());
    }

    @Test
    void staleVersionTakesPrecedenceOverInvalidContentAndDoesNotSave() {
        Fixture fixture = new Fixture();
        fixture.posts.current = originalPost("stale-id", "stale-post");

        BlogException exception = assertThrows(BlogException.class, () -> fixture.postsService.update(
                "stale-post", "another-id:1", command("stale-post", " ", null)));

        assertSame(BlogErrorCode.VERSION_CONFLICT, exception.code());
        assertTrue(fixture.posts.saved.isEmpty());
        assertTrue(fixture.posts.archived.isEmpty());
        assertEquals(1, fixture.transactions.calls);
        assertTrue(fixture.transactions.outsideTransactionCalls.isEmpty());
    }

    @Test
    void inboxLedgerDeduplicatesInsideTheSameTransactionRunner() {
        Fixture fixture = new Fixture();
        byte[] markdown = "# Inbox".getBytes(StandardCharsets.UTF_8);

        MarkdownImportOutcome imported = fixture.markdownService.importOnce("inbox-post.md", markdown);

        assertSame(MarkdownImportOutcome.IMPORTED, imported);
        assertEquals(List.of(
                        "tx.begin",
                        "ledger.lock",
                        "ledger.contains",
                        "markdown.parse",
                        "authors.find",
                        "posts.add:IMPORT",
                        "ledger.record",
                        "tx.end"),
                fixture.transactions.trace);
        assertEquals(1, fixture.ledger.records.size());
        assertEquals(fixture.ledger.checkedHashes.getFirst(), fixture.ledger.records.getFirst().sha256());
        assertEquals("inbox-post.md", fixture.ledger.records.getFirst().fileName());
        assertEquals("inbox-post", fixture.ledger.records.getFirst().slug());
        assertEquals(NOW, fixture.ledger.records.getFirst().importedAt());
        assertEquals(PostRevisionEventType.IMPORT, fixture.posts.added.getFirst().eventType());
        assertEquals(64, fixture.ledger.records.getFirst().sha256().length());
        assertTrue(fixture.transactions.outsideTransactionCalls.isEmpty());

        fixture.transactions.trace.clear();
        MarkdownImportOutcome duplicate = fixture.markdownService.importOnce("inbox-post.md", markdown);

        assertSame(MarkdownImportOutcome.ALREADY_IMPORTED, duplicate);
        assertEquals(List.of("tx.begin", "ledger.lock", "ledger.contains", "tx.end"),
                fixture.transactions.trace);
        assertEquals(1, fixture.posts.added.size());
        assertEquals(1, fixture.markdown.parseRequests.size());
        assertEquals(1, fixture.ledger.records.size());
        assertEquals(2, fixture.transactions.calls);
        assertTrue(fixture.transactions.outsideTransactionCalls.isEmpty());
    }

    @Test
    void mediaServiceMapsStorageBoundaryModelsWithoutExposingThemInbound() {
        Fixture fixture = new Fixture();

        StoredMediaResult stored = fixture.mediaService.storeMedia("fake.png", "image/png", new byte[]{1, 2});
        MediaContentResult content = fixture.mediaService.readPublicMedia(stored.relativePath());

        assertEquals("/media/fake.png", stored.url());
        assertEquals("fake.png", stored.relativePath());
        assertEquals("image/png", stored.mimeType());
        assertEquals(2, stored.size());
        assertEquals("image/png", content.mimeType());
        assertTrue(Arrays.equals(new byte[]{1}, content.bytes()));
    }

    @Test
    void applicationResultsAndInputPortsDoNotExposeDomainTypes() {
        List<Class<?>> resultTypes = List.of(
                ArchivedPostResult.class,
                AuthorResult.class,
                ContentScanErrorResult.class,
                MarkdownImportOutcome.class,
                MediaContentResult.class,
                PostDetailResult.class,
                PostListResult.class,
                PostSummaryResult.class,
                StoredMediaResult.class
        );

        for (Class<?> resultType : resultTypes) {
            assertTrue(resultType.getPackageName().startsWith("com.speaive.blog.application.result"));
            if (resultType.isRecord()) {
                Arrays.stream(resultType.getRecordComponents()).forEach(component -> assertFalse(
                        referencesDomain(component.getGenericType()),
                        () -> resultType.getSimpleName() + "." + component.getName() + " 泄露了 domain 类型"));
            }
        }

        List.of(PostUseCase.class, MediaUseCase.class, MarkdownUseCase.class, MarkdownInboxUseCase.class).stream()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .forEach(method -> {
                    assertFalse(referencesDomain(method.getGenericReturnType()),
                            () -> method + " 的返回值泄露了 domain 类型");
                    Arrays.stream(method.getGenericParameterTypes()).forEach(parameter -> assertFalse(
                            referencesDomain(parameter), () -> method + " 的参数泄露了 domain 类型"));
                });
    }

    private static PostWriteCommand command(String slug, String title, Instant publishedAt) {
        return new PostWriteCommand(slug, title, "摘要", publishedAt, List.of("测试"), null, title + " body");
    }

    private static Post originalPost(String id, String slug) {
        return Post.createDraft(id, slug,
                new PostContent("原文", "摘要", CREATED_AT, List.of("测试"), null, "原始正文"),
                Author.ADMIN, CREATED_AT);
    }

    private static boolean referencesDomain(Type type) {
        if (type instanceof Class<?> concrete) {
            return concrete.getName().startsWith("com.speaive.blog.domain.")
                    || concrete.isArray() && referencesDomain(concrete.getComponentType());
        }
        if (type instanceof ParameterizedType parameterized) {
            return referencesDomain(parameterized.getRawType())
                    || Arrays.stream(parameterized.getActualTypeArguments())
                    .anyMatch(ApplicationServicesTests::referencesDomain);
        }
        if (type instanceof GenericArrayType array) {
            return referencesDomain(array.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getUpperBounds()).anyMatch(ApplicationServicesTests::referencesDomain)
                    || Arrays.stream(wildcard.getLowerBounds()).anyMatch(ApplicationServicesTests::referencesDomain);
        }
        if (type instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds()).anyMatch(ApplicationServicesTests::referencesDomain);
        }
        return false;
    }

    private static final class Fixture {
        private final FakeTransactionRunner transactions = new FakeTransactionRunner();
        private final FakePostRepository posts = new FakePostRepository(transactions);
        private final FakeAuthorRepository authors = new FakeAuthorRepository(transactions, Author.ADMIN);
        private final FakeMarkdownPort markdown = new FakeMarkdownPort(transactions);
        private final FakeMediaStoragePort media = new FakeMediaStoragePort();
        private final FakeMarkdownImportLedger ledger = new FakeMarkdownImportLedger(transactions);
        private final PostApplicationService postsService = new PostApplicationService(
                posts, authors, markdown, transactions, CLOCK);
        private final MarkdownApplicationService markdownService = new MarkdownApplicationService(
                posts, authors, markdown, ledger, transactions, CLOCK);
        private final MediaApplicationService mediaService = new MediaApplicationService(media);
    }

    private static final class FakeTransactionRunner implements TransactionRunner {
        private final List<String> trace = new ArrayList<>();
        private final List<String> outsideTransactionCalls = new ArrayList<>();
        private int calls;
        private boolean active;

        @Override
        public <T> T required(Supplier<T> action) {
            if (active) {
                throw new AssertionError("测试用 TransactionRunner 不接受嵌套事务");
            }
            calls++;
            trace.add("tx.begin");
            active = true;
            try {
                return action.get();
            } finally {
                active = false;
                trace.add("tx.end");
            }
        }

        private void observe(String operation) {
            trace.add(operation);
            if (!active) {
                outsideTransactionCalls.add(operation);
            }
        }
    }

    private static final class FakePostRepository implements PostRepository {
        private final FakeTransactionRunner transactions;
        private final List<AddedPost> added = new ArrayList<>();
        private final List<PostChange> saved = new ArrayList<>();
        private final List<PostChange> archived = new ArrayList<>();
        private Post current;

        private FakePostRepository(FakeTransactionRunner transactions) {
            this.transactions = transactions;
        }

        @Override
        public List<PostSummary> findAll(PostQueryScope scope) {
            if (current == null || scope == PostQueryScope.PUBLISHED && current.status() != PostStatus.PUBLISHED) {
                return List.of();
            }
            return List.of(new PostSummary(
                    current.id(),
                    current.slugValue(),
                    current.title(),
                    current.description(),
                    current.publishedAt(),
                    current.updatedAt(),
                    current.tags(),
                    current.cover(),
                    current.author(),
                    current.status(),
                    current.revision()
            ));
        }

        @Override
        public Optional<Post> findBySlug(String slug, PostQueryScope scope) {
            if (current == null || !current.slug().equals(slug)
                    || scope == PostQueryScope.PUBLISHED && current.status() != PostStatus.PUBLISHED) {
                return Optional.empty();
            }
            return Optional.of(current);
        }

        @Override
        public Optional<Post> findById(String postId, PostQueryScope scope) {
            if (current == null || !current.id().equals(postId)
                    || scope == PostQueryScope.PUBLISHED && current.status() != PostStatus.PUBLISHED) {
                return Optional.empty();
            }
            return Optional.of(current);
        }

        @Override
        public Optional<Post> lockBySlug(String slug) {
            transactions.observe("posts.lock");
            return Optional.ofNullable(current).filter(post -> post.slug().equals(slug));
        }

        @Override
        public void add(Post post, PostRevisionEventType eventType, Set<String> mediaPaths) {
            transactions.observe("posts.add:" + eventType.name());
            added.add(new AddedPost(post, eventType));
            current = post;
        }

        @Override
        public void save(PostChange change, Set<String> mediaPaths) {
            transactions.observe("posts.save:" + change.eventType().name());
            saved.add(change);
            current = change.current();
        }

        @Override
        public ArchiveReceipt archive(PostChange change) {
            transactions.observe("posts.archive");
            archived.add(change);
            current = null;
            return new ArchiveReceipt(change.current().slug(), change.current().status(),
                    "fake:" + change.current().id() + ":" + change.current().revision());
        }
    }

    private static final class FakeAuthorRepository implements AuthorRepository {
        private final FakeTransactionRunner transactions;
        private Author author;
        private final List<String> requestedIds = new ArrayList<>();

        private FakeAuthorRepository(FakeTransactionRunner transactions, Author author) {
            this.transactions = transactions;
            this.author = author;
        }

        @Override
        public Optional<Author> findById(String id) {
            transactions.observe("authors.find");
            requestedIds.add(id);
            return author.id().equals(id) ? Optional.of(author) : Optional.empty();
        }
    }

    private static final class FakeMarkdownPort implements MarkdownPort {
        private final FakeTransactionRunner transactions;
        private final List<MarkdownParseRequest> parseRequests = new ArrayList<>();
        private String normalizedSlug;

        private FakeMarkdownPort(FakeTransactionRunner transactions) {
            this.transactions = transactions;
        }

        @Override
        public ParsedPostDocument parse(MarkdownParseRequest request) {
            transactions.observe("markdown.parse");
            parseRequests.add(request);
            String fileName = request.fileName();
            String slug = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
            return new ParsedPostDocument(slug, "导入文章", "导入摘要", request.fallbackPublishedAt(),
                    List.of("导入"), null, new String(request.markdown(), StandardCharsets.UTF_8));
        }

        @Override
        public ParsedPostDocument normalize(PostWriteCommand command, String slug, Instant publishedAt) {
            transactions.observe("markdown.normalize");
            String resultSlug = normalizedSlug == null ? slug : normalizedSlug;
            return new ParsedPostDocument(resultSlug, command.title(), command.description(), publishedAt,
                    command.tags(), command.cover(), PostVisibility.valueOf(command.visibility()), command.body());
        }

        @Override
        public String render(String markdown) {
            return "<p>" + markdown + "</p>";
        }

        @Override
        public Set<String> referencedMediaPaths(String body, String cover) {
            return Set.of();
        }
    }

    private static final class FakeMediaStoragePort implements MediaStoragePort {
        @Override
        public StoredMedia store(String fileName, String declaredMimeType, byte[] bytes) {
            return new StoredMedia("/media/fake.png", "fake.png", "image/png", bytes.length);
        }

        @Override
        public MediaContent read(String relativePath, MediaReadScope scope) {
            return new MediaContent("image/png", new byte[]{1});
        }
    }

    private static final class FakeMarkdownImportLedger implements MarkdownImportLedger {
        private final FakeTransactionRunner transactions;
        private final List<String> checkedHashes = new ArrayList<>();
        private final List<LedgerRecord> records = new ArrayList<>();

        private FakeMarkdownImportLedger(FakeTransactionRunner transactions) {
            this.transactions = transactions;
        }

        @Override
        public void lockForImport(String sha256) {
            transactions.observe("ledger.lock");
        }

        @Override
        public boolean contains(String sha256) {
            transactions.observe("ledger.contains");
            checkedHashes.add(sha256);
            return records.stream().anyMatch(record -> record.sha256().equals(sha256));
        }

        @Override
        public void record(String sha256, String fileName, String slug, Instant importedAt) {
            transactions.observe("ledger.record");
            records.add(new LedgerRecord(sha256, fileName, slug, importedAt));
        }
    }

    private record AddedPost(Post post, PostRevisionEventType eventType) {
    }

    private record LedgerRecord(String sha256, String fileName, String slug, Instant importedAt) {
    }
}
