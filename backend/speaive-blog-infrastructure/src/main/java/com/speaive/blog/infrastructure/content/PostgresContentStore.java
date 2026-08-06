package com.speaive.blog.infrastructure.content;

import com.speaive.blog.application.BlogErrorCode;
import com.speaive.blog.application.BlogException;
import com.speaive.blog.application.ContentStorePort;
import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.domain.ArchivedPost;
import com.speaive.blog.domain.Author;
import com.speaive.blog.domain.MediaContent;
import com.speaive.blog.domain.Post;
import com.speaive.blog.domain.PostCollection;
import com.speaive.blog.domain.PostStatus;
import com.speaive.blog.domain.StoredMedia;
import com.speaive.blog.infrastructure.content.MarkdownCodec.ParseOptions;
import com.speaive.blog.infrastructure.content.MarkdownCodec.ParsedMarkdown;
import com.speaive.blog.infrastructure.content.MarkdownCodec.PostDocument;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PostgresContentStore implements ContentStorePort {
    private final BlogPersistenceMapper mapper;
    private final MediaFileStore mediaFiles;
    private final Path mediaDirectory;
    private final long maxMarkdownBytes;
    private final MarkdownCodec markdown = new MarkdownCodec();
    private final Clock clock;

    public PostgresContentStore(BlogPersistenceMapper mapper, FileContentStoreSettings settings) {
        this(mapper, settings, Clock.systemUTC());
    }

    PostgresContentStore(BlogPersistenceMapper mapper, FileContentStoreSettings settings, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.mediaFiles = new MediaFileStore(settings, clock);
        this.mediaDirectory = settings.dataDirectory().resolve("media").toAbsolutePath().normalize();
        this.maxMarkdownBytes = settings.maxMarkdownBytes();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public PostCollection scan(boolean includeDrafts) {
        List<BlogPostEntity> rows = includeDrafts
                ? mapper.selectAllSummaries()
                : mapper.selectPublishedSummaries();
        return new PostCollection(rows.stream().map(this::toSummary).toList(), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Post> find(String slug, boolean includeDrafts) {
        String normalizedSlug = MarkdownCodec.validateSlug(slug);
        BlogPostEntity row = includeDrafts
                ? mapper.selectBySlug(normalizedSlug)
                : mapper.selectPublishedBySlug(normalizedSlug);
        return Optional.ofNullable(row).map(this::toDetail);
    }

    @Override
    @Transactional
    public Post createDraft(PostWriteCommand command) {
        return createDraft(command, "CREATE");
    }

    @Override
    @Transactional
    public Post update(String slug, String expectedVersion, PostWriteCommand command) {
        String normalizedSlug = MarkdownCodec.validateSlug(slug);
        ExpectedVersion expected = parseVersion(expectedVersion);
        BlogPostEntity current = lockRequired(normalizedSlug);
        assertVersion(current, expected);

        Instant now = clock.instant();
        NormalizedPost normalized = normalize(command, normalizedSlug,
                command.publishedAt() == null ? current.getPublishedAt() : command.publishedAt(), now);
        BlogPostEntity next = copy(current);
        apply(next, normalized);
        next.setUpdatedAt(now);
        next.setRevision(expected.revision() + 1);

        updateWithRevisionCheck(next, expected.revision());
        replaceTags(next.getId(), normalized.tags());
        writeRevision(next, "UPDATE", now);
        return toDetail(next);
    }

    @Override
    @Transactional
    public Post transition(String slug, PostStatus targetStatus, String expectedVersion) {
        String normalizedSlug = MarkdownCodec.validateSlug(slug);
        ExpectedVersion expected = parseVersion(expectedVersion);
        BlogPostEntity current = lockRequired(normalizedSlug);
        assertVersion(current, expected);

        Instant now = clock.instant();
        BlogPostEntity next = copy(current);
        next.setStatus(Objects.requireNonNull(targetStatus, "targetStatus"));
        next.setUpdatedAt(now);
        next.setRevision(expected.revision() + 1);
        updateWithRevisionCheck(next, expected.revision());
        writeRevision(next, targetStatus == PostStatus.PUBLISHED ? "PUBLISH" : "UNPUBLISH", now);
        return toDetail(next);
    }

    @Override
    @Transactional
    public ArchivedPost archive(String slug, String expectedVersion) {
        String normalizedSlug = MarkdownCodec.validateSlug(slug);
        ExpectedVersion expected = parseVersion(expectedVersion);
        BlogPostEntity current = lockRequired(normalizedSlug);
        assertVersion(current, expected);

        Instant now = clock.instant();
        BlogPostEntity archived = copy(current);
        archived.setUpdatedAt(now);
        archived.setRevision(expected.revision() + 1);
        updateWithRevisionCheck(archived, expected.revision());
        writeRevision(archived, "ARCHIVE", now);
        if (mapper.deleteCas(archived.getId(), normalizedSlug, archived.getRevision()) != 1) {
            throw versionConflict();
        }
        return new ArchivedPost(normalizedSlug, current.getStatus(),
                "database:" + current.getId() + ":" + archived.getRevision());
    }

    @Override
    @Transactional
    public Post importDraft(String fileName, byte[] markdownBytes) {
        validateMarkdownFileName(fileName);
        assertMarkdownSize(markdownBytes.length);
        String fallbackSlug = MarkdownCodec.validateSlug(fileName.substring(0, fileName.length() - 3));
        ParsedMarkdown parsed = markdown.parse(markdownBytes,
                new ParseOptions(null, fallbackSlug, fallbackSlug.replace('-', ' '), clock.instant()));
        return createDraft(new PostWriteCommand(parsed.slug(), parsed.title(), parsed.description(),
                parsed.publishedAt(), parsed.tags(), parsed.cover(), parsed.body()), "IMPORT");
    }

    @Override
    @Transactional
    public StoredMedia storeMedia(String fileName, String declaredMimeType, byte[] bytes) {
        if (fileName != null && fileName.codePointCount(0, fileName.length()) > 255) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME, "图片文件名不能超过 255 个字符");
        }
        StoredMedia stored = mediaFiles.store(fileName, declaredMimeType, bytes);
        boolean synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
        if (synchronizedTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        deleteMediaFile(stored.relativePath());
                    }
                }
            });
        }

        BlogMediaEntity media = new BlogMediaEntity();
        media.setRelativePath(stored.relativePath());
        media.setOriginalFileName(fileName);
        media.setMimeType(stored.mimeType());
        media.setSizeBytes(stored.size());
        media.setSha256(sha256(bytes));
        media.setCreatedAt(clock.instant());
        try {
            mapper.insertMedia(media);
        } catch (RuntimeException exception) {
            if (!synchronizedTransaction) {
                deleteMediaFile(stored.relativePath());
            }
            throw exception;
        }
        return stored;
    }

    @Override
    @Transactional(readOnly = true)
    public MediaContent readMedia(String relativePath) {
        BlogMediaEntity registered = mapper.selectMedia(relativePath);
        if (registered == null) {
            throw new BlogException(BlogErrorCode.NOT_FOUND, "图片不存在");
        }
        MediaContent content;
        try {
            content = mediaFiles.read(relativePath);
        } catch (BlogException exception) {
            if (exception.code() == BlogErrorCode.STORAGE_ERROR) {
                throw exception;
            }
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "图片文件与数据库记录不一致", exception);
        }
        if (!registered.getMimeType().equals(content.mimeType())
                || registered.getSizeBytes() != content.bytes().length
                || !registered.getSha256().equals(sha256(content.bytes()))) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "图片文件与数据库记录不一致");
        }
        return content;
    }

    @Override
    public String renderMarkdown(String source) {
        byte[] bytes = Objects.requireNonNullElse(source, "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertMarkdownSize(bytes.length);
        return markdown.render(source);
    }

    private Post createDraft(PostWriteCommand command, String eventType) {
        if (command == null) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "文章内容不能为空");
        }
        String slug = MarkdownCodec.validateSlug(command.slug());
        Instant now = clock.instant();
        NormalizedPost normalized = normalize(command, slug,
                command.publishedAt() == null ? now : command.publishedAt(), now);
        BlogPostEntity entity = new BlogPostEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setSlug(slug);
        applyAuthor(entity, Author.ADMIN);
        entity.setStatus(PostStatus.DRAFT);
        entity.setRevision(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        apply(entity, normalized);
        try {
            mapper.insert(entity);
            insertTags(entity.getId(), normalized.tags());
            writeRevision(entity, eventType, now);
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.SLUG_CONFLICT, "slug 已存在：" + slug, exception);
        }
        return toDetail(entity);
    }

    private NormalizedPost normalize(
            PostWriteCommand command, String slug, Instant publishedAt, Instant updatedAt) {
        if (command == null) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "文章内容不能为空");
        }
        PostDocument document = new PostDocument(slug, command.title(), command.description(), publishedAt,
                updatedAt, command.tags(), command.cover(), command.body());
        byte[] serialized = markdown.serialize(document);
        assertMarkdownSize(serialized.length);
        ParsedMarkdown parsed = markdown.parse(serialized,
                new ParseOptions(slug, slug, slug.replace('-', ' '), publishedAt));
        return new NormalizedPost(parsed.title(), parsed.description(), parsed.publishedAt(), parsed.tags(),
                parsed.cover(), parsed.body());
    }

    private BlogPostEntity lockRequired(String slug) {
        BlogPostEntity current = mapper.lockBySlug(slug);
        if (current == null) {
            throw new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在");
        }
        return current;
    }

    private void updateWithRevisionCheck(BlogPostEntity next, long expectedRevision) {
        if (mapper.updateCas(next, expectedRevision) != 1) {
            throw versionConflict();
        }
    }

    private void replaceTags(String postId, List<String> tags) {
        mapper.deleteTags(postId);
        insertTags(postId, tags);
    }

    private void insertTags(String postId, List<String> tags) {
        if (!tags.isEmpty()) {
            mapper.insertTags(postId, tags);
        }
    }

    private void writeRevision(BlogPostEntity post, String eventType, Instant recordedAt) {
        if (mapper.insertRevisionSnapshot(post.getId(), eventType, recordedAt) != 1) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "写入文章修订记录失败");
        }
        if (!mapper.selectTags(post.getId()).isEmpty()) {
            mapper.insertRevisionTags(post.getId(), post.getRevision());
        }
    }

    private Post toSummary(BlogPostEntity entity) {
        return new Post(entity.getSlug(), entity.getTitle(), entity.getDescription(), entity.getPublishedAt(),
                entity.getUpdatedAt(), mapper.selectTags(entity.getId()), entity.getCover(), toAuthor(entity),
                entity.getStatus(), "", "", versionOf(entity));
    }

    private Post toDetail(BlogPostEntity entity) {
        String body = Objects.requireNonNullElse(entity.getBody(), "");
        return new Post(entity.getSlug(), entity.getTitle(), entity.getDescription(), entity.getPublishedAt(),
                entity.getUpdatedAt(), mapper.selectTags(entity.getId()), entity.getCover(), toAuthor(entity),
                entity.getStatus(), body, markdown.render(body), versionOf(entity));
    }

    private static Author toAuthor(BlogPostEntity entity) {
        return new Author(entity.getAuthorId(), entity.getAuthorUsername(), entity.getAuthorDisplayName(),
                entity.getAuthorType(), entity.getAuthorAvatarUrl());
    }

    private static void applyAuthor(BlogPostEntity entity, Author author) {
        entity.setAuthorId(author.id());
        entity.setAuthorUsername(author.username());
        entity.setAuthorDisplayName(author.displayName());
        entity.setAuthorType(author.type());
        entity.setAuthorAvatarUrl(author.avatarUrl());
    }

    private static void apply(BlogPostEntity entity, NormalizedPost normalized) {
        entity.setTitle(normalized.title());
        entity.setDescription(normalized.description());
        entity.setPublishedAt(normalized.publishedAt());
        entity.setBody(normalized.body());
        entity.setCover(normalized.cover());
    }

    private static BlogPostEntity copy(BlogPostEntity source) {
        BlogPostEntity copy = new BlogPostEntity();
        copy.setId(source.getId());
        copy.setSlug(source.getSlug());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setPublishedAt(source.getPublishedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setStatus(source.getStatus());
        copy.setBody(source.getBody());
        copy.setCover(source.getCover());
        copy.setAuthorId(source.getAuthorId());
        copy.setAuthorUsername(source.getAuthorUsername());
        copy.setAuthorDisplayName(source.getAuthorDisplayName());
        copy.setAuthorType(source.getAuthorType());
        copy.setAuthorAvatarUrl(source.getAuthorAvatarUrl());
        copy.setRevision(source.getRevision());
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }

    private static ExpectedVersion parseVersion(String version) {
        try {
            int separator = version.lastIndexOf(':');
            String postId = version.substring(0, separator);
            long revision = Long.parseLong(version.substring(separator + 1));
            if (!postId.isBlank() && revision > 0) {
                return new ExpectedVersion(postId, revision);
            }
        } catch (IndexOutOfBoundsException | NumberFormatException | NullPointerException ignored) {
            // Versions are opaque API tokens. A malformed token is indistinguishable from a stale one.
        }
        throw versionConflict();
    }

    private static void assertVersion(BlogPostEntity current, ExpectedVersion expected) {
        if (!current.getId().equals(expected.postId()) || current.getRevision() != expected.revision()) {
            throw versionConflict();
        }
    }

    private static String versionOf(BlogPostEntity post) {
        return post.getId() + ":" + post.getRevision();
    }

    private static BlogException versionConflict() {
        return new BlogException(BlogErrorCode.VERSION_CONFLICT, "文章已被其他操作更新，请刷新后重试");
    }

    private void assertMarkdownSize(long size) {
        if (size > maxMarkdownBytes) {
            throw new BlogException(BlogErrorCode.TOO_LARGE,
                    "Markdown 文件不能超过 " + maxMarkdownBytes + " 字节");
        }
    }

    private static void validateMarkdownFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")
                || fileName.equals(".") || fileName.equals("..") || !fileName.endsWith(".md")
                || fileName.codePointCount(0, fileName.length()) > 255) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME,
                    "Markdown 文件名必须是单层文件名，扩展名为小写 .md");
        }
    }

    private void deleteMediaFile(String relativePath) {
        Path target = mediaDirectory.resolve(relativePath).normalize();
        if (!target.startsWith(mediaDirectory)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // A failed compensation leaves an unregistered file, which readMedia never exposes.
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record NormalizedPost(
            String title,
            String description,
            Instant publishedAt,
            List<String> tags,
            String cover,
            String body) {
    }

    private record ExpectedVersion(String postId, long revision) {
    }
}
