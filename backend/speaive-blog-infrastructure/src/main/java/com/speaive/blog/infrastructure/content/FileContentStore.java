package com.speaive.blog.infrastructure.content;

import com.speaive.blog.application.BlogErrorCode;
import com.speaive.blog.application.BlogException;
import com.speaive.blog.application.ContentStorePort;
import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.domain.ArchivedPost;
import com.speaive.blog.domain.Author;
import com.speaive.blog.domain.ContentError;
import com.speaive.blog.domain.MediaContent;
import com.speaive.blog.domain.Post;
import com.speaive.blog.domain.PostCollection;
import com.speaive.blog.domain.PostStatus;
import com.speaive.blog.domain.StoredMedia;
import com.speaive.blog.infrastructure.content.MarkdownCodec.ParseOptions;
import com.speaive.blog.infrastructure.content.MarkdownCodec.ParsedMarkdown;
import com.speaive.blog.infrastructure.content.MarkdownCodec.PostDocument;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class FileContentStore implements ContentStorePort {
    private static final DateTimeFormatter ARCHIVE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmssSSS")
            .withZone(ZoneOffset.UTC);

    private final Path dataDirectory;
    private final Path postsDirectory;
    private final Path draftsDirectory;
    private final Path archiveDirectory;
    private final long maxMarkdownBytes;
    private final MediaFileStore mediaFiles;
    private final MarkdownCodec markdown = new MarkdownCodec();
    private final Clock clock;
    private final BeforeFileCommit beforeFileCommit;
    private final SlugLockRegistry locks = new SlugLockRegistry();

    public FileContentStore(FileContentStoreSettings settings) {
        this(settings, Clock.systemUTC());
    }

    FileContentStore(FileContentStoreSettings settings, Clock clock) {
        this(settings, clock, (source, target, replaceExisting) -> {
        });
    }

    FileContentStore(FileContentStoreSettings settings, Clock clock, BeforeFileCommit beforeFileCommit) {
        this.dataDirectory = settings.dataDirectory();
        this.postsDirectory = dataDirectory.resolve("posts");
        this.draftsDirectory = dataDirectory.resolve("drafts");
        this.archiveDirectory = dataDirectory.resolve("archive");
        this.maxMarkdownBytes = settings.maxMarkdownBytes();
        this.clock = clock;
        this.beforeFileCommit = beforeFileCommit;
        this.mediaFiles = new MediaFileStore(settings, clock, beforeFileCommit);
        initializeDirectories();
    }

    @Override
    public PostCollection scan(boolean includeDrafts) {
        initializeDirectories();
        ScanResult published = scanDirectory(postsDirectory, PostStatus.PUBLISHED);
        ScanResult drafts = includeDrafts
                ? scanDirectory(draftsDirectory, PostStatus.DRAFT)
                : new ScanResult(List.of(), List.of());

        Map<String, Post> bySlug = new LinkedHashMap<>();
        List<ContentError> errors = new ArrayList<>();
        errors.addAll(published.errors());
        errors.addAll(drafts.errors());
        for (Post post : concat(published.posts(), drafts.posts())) {
            if (bySlug.putIfAbsent(post.slug(), post) != null) {
                errors.add(new ContentError(post.slug() + ".md", post.status(),
                        "posts 和 drafts 中存在重复 slug：" + post.slug()));
            }
        }
        List<Post> posts = new ArrayList<>(bySlug.values());
        posts.sort(Comparator.comparing(Post::publishedAt).reversed().thenComparing(Post::slug));
        return new PostCollection(posts, errors);
    }

    @Override
    public Optional<Post> find(String slug, boolean includeDrafts) {
        String normalizedSlug = MarkdownCodec.validateSlug(slug);
        initializeDirectories();
        Optional<StoredPost> published = readIfExists(postsDirectory, normalizedSlug, PostStatus.PUBLISHED);
        if (published.isPresent()) {
            return Optional.of(published.get().post());
        }
        return includeDrafts
                ? readIfExists(draftsDirectory, normalizedSlug, PostStatus.DRAFT).map(StoredPost::post)
                : Optional.empty();
    }

    @Override
    public Post createDraft(PostWriteCommand command) {
        String slug = MarkdownCodec.validateSlug(command.slug());
        return withLock(slug, () -> {
            initializeDirectories();
            if (existsNoFollow(postsDirectory.resolve(slug + ".md"))
                    || existsNoFollow(draftsDirectory.resolve(slug + ".md"))) {
                throw new BlogException(BlogErrorCode.SLUG_CONFLICT, "slug 已存在：" + slug);
            }
            Instant now = clock.instant();
            PostDocument document = toDocument(command, slug,
                    command.publishedAt() == null ? now : command.publishedAt(), now);
            byte[] bytes = serializeWithinLimit(document);
            Path target = draftsDirectory.resolve(slug + ".md");
            atomicWrite(target, bytes, false, null);
            return readRequired(target, PostStatus.DRAFT).post();
        });
    }

    @Override
    public Post update(String slug, String expectedVersion, PostWriteCommand command) {
        String normalizedSlug = MarkdownCodec.validateSlug(slug);
        requireVersion(expectedVersion);
        return withLock(normalizedSlug, () -> {
            StoredPost current = findStoredRequired(normalizedSlug);
            assertVersion(current.post(), expectedVersion);
            PostDocument document = toDocument(command, normalizedSlug,
                    command.publishedAt() == null ? current.post().publishedAt() : command.publishedAt(),
                    clock.instant());
            byte[] bytes = serializeWithinLimit(document);
            atomicWrite(current.path(), bytes, true, expectedVersion);
            return readRequired(current.path(), current.post().status()).post();
        });
    }

    @Override
    public Post transition(String slug, PostStatus targetStatus, String expectedVersion) {
        String normalizedSlug = MarkdownCodec.validateSlug(slug);
        requireVersion(expectedVersion);
        return withLock(normalizedSlug, () -> {
            StoredPost current = findStoredRequired(normalizedSlug);
            assertVersion(current.post(), expectedVersion);
            if (current.post().status() == targetStatus) {
                return current.post();
            }

            Path targetDirectory = targetStatus == PostStatus.PUBLISHED ? postsDirectory : draftsDirectory;
            assertSafeExistingDirectory(targetDirectory);
            Path target = targetDirectory.resolve(normalizedSlug + ".md");
            if (existsNoFollow(target)) {
                throw new BlogException(BlogErrorCode.SLUG_CONFLICT,
                        "目标目录已存在同名文章：" + normalizedSlug);
            }
            // Status is represented by the containing directory. Moving the unchanged file keeps the
            // content version stable and avoids mutating the source before a no-replace target is secured.
            atomicMove(current.path(), target, false, expectedVersion, null, false);
            return readRequired(target, targetStatus).post();
        });
    }

    @Override
    public ArchivedPost archive(String slug, String expectedVersion) {
        String normalizedSlug = MarkdownCodec.validateSlug(slug);
        requireVersion(expectedVersion);
        return withLock(normalizedSlug, () -> {
            StoredPost current = findStoredRequired(normalizedSlug);
            assertVersion(current.post(), expectedVersion);
            String archiveName = normalizedSlug + "-" + ARCHIVE_TIMESTAMP.format(clock.instant()) + "-"
                    + UUID.randomUUID().toString().substring(0, 8) + ".md";
            Path target = archiveDirectory.resolve(archiveName);
            assertSafeExistingDirectory(archiveDirectory);
            atomicMove(current.path(), target, false, expectedVersion, null, false);
            return new ArchivedPost(normalizedSlug, current.post().status(), archiveName);
        });
    }

    @Override
    public Post importDraft(String fileName, byte[] markdownBytes) {
        validatePlainFileName(fileName, ".md", "Markdown 文件扩展名必须是小写 .md");
        assertMarkdownSize(markdownBytes.length);
        String slug = MarkdownCodec.validateSlug(fileName.substring(0, fileName.length() - 3));
        ParsedMarkdown parsed = markdown.parse(markdownBytes,
                new ParseOptions(null, slug, slug.replace('-', ' '), clock.instant()));
        return createDraft(new PostWriteCommand(parsed.slug(), parsed.title(), parsed.description(),
                parsed.publishedAt(), parsed.tags(), parsed.cover(), parsed.body()));
    }

    @Override
    public StoredMedia storeMedia(String fileName, String declaredMimeType, byte[] bytes) {
        return mediaFiles.store(fileName, declaredMimeType, bytes);
    }

    @Override
    public MediaContent readMedia(String relativePath) {
        return mediaFiles.read(relativePath);
    }

    @Override
    public String renderMarkdown(String source) {
        byte[] bytes = (source == null ? "" : source).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertMarkdownSize(bytes.length);
        return markdown.render(source);
    }

    private ScanResult scanDirectory(Path directory, PostStatus status) {
        List<Post> posts = new ArrayList<>();
        List<ContentError> errors = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!name.endsWith(".md")) {
                    if (name.toLowerCase(Locale.ROOT).endsWith(".mdx")) {
                        errors.add(new ContentError(name, status, "不允许使用 MDX，只接受 .md 文件"));
                    }
                    continue;
                }
                try {
                    String slug = MarkdownCodec.validateSlug(name.substring(0, name.length() - 3));
                    posts.add(readRequired(entry, status, slug).post());
                } catch (BlogException exception) {
                    errors.add(new ContentError(name, status, exception.getMessage()));
                }
            }
        } catch (IOException exception) {
            throw storageError("读取内容目录失败", exception);
        }
        return new ScanResult(posts, errors);
    }

    private Optional<StoredPost> readIfExists(Path directory, String slug, PostStatus status) {
        Path file = directory.resolve(slug + ".md");
        if (!existsNoFollow(file)) {
            return Optional.empty();
        }
        return Optional.of(readRequired(file, status, slug));
    }

    private StoredPost readRequired(Path path, PostStatus status) {
        String name = path.getFileName().toString();
        return readRequired(path, status, name.substring(0, name.length() - 3));
    }

    private StoredPost readRequired(Path path, PostStatus status, String expectedSlug) {
        BasicFileAttributes attributes = regularFileAttributes(path, BlogErrorCode.NOT_FOUND, "文章不存在");
        assertMarkdownSize(attributes.size());
        byte[] bytes = readBytes(path, maxMarkdownBytes);
        ParsedMarkdown parsed = markdown.parse(bytes,
                new ParseOptions(expectedSlug, expectedSlug, expectedSlug.replace('-', ' '),
                        attributes.lastModifiedTime().toInstant()));
        Instant updatedAt = parsed.updatedAt() == null ? attributes.lastModifiedTime().toInstant() : parsed.updatedAt();
        Post post = new Post(parsed.slug(), parsed.title(), parsed.description(), parsed.publishedAt(), updatedAt,
                parsed.tags(), parsed.cover(), Author.ADMIN, status, parsed.body(), parsed.html(), sha256(bytes));
        return new StoredPost(path, post);
    }

    private StoredPost findStoredRequired(String slug) {
        Optional<StoredPost> published = readIfExists(postsDirectory, slug, PostStatus.PUBLISHED);
        if (published.isPresent()) {
            return published.get();
        }
        return readIfExists(draftsDirectory, slug, PostStatus.DRAFT)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在"));
    }

    private PostDocument toDocument(PostWriteCommand command, String slug, Instant publishedAt, Instant updatedAt) {
        if (command == null) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "文章内容不能为空");
        }
        if (command.slug() != null && !MarkdownCodec.validateSlug(command.slug()).equals(slug)) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "文章 slug 不能修改");
        }
        return new PostDocument(slug, command.title(), command.description(), publishedAt, updatedAt,
                command.tags(), command.cover(), command.body());
    }

    private byte[] serializeWithinLimit(PostDocument document) {
        byte[] bytes = markdown.serialize(document);
        assertMarkdownSize(bytes.length);
        return bytes;
    }

    private void initializeDirectories() {
        ensureDirectory(dataDirectory);
        ensureDirectory(postsDirectory);
        ensureDirectory(draftsDirectory);
        mediaFiles.initializeDirectories();
        ensureDirectory(archiveDirectory);
    }

    private void ensureDirectory(Path directory) {
        assertInsideDataDirectory(directory);
        ensureDataRoot();
        if (directory.toAbsolutePath().normalize().equals(dataDirectory)) {
            return;
        }

        Path current = dataDirectory;
        Path relative = dataDirectory.relativize(directory.toAbsolutePath().normalize());
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // Another writer created the path. It is validated below before it is used.
                } catch (IOException exception) {
                    throw storageError("创建数据目录失败", exception);
                }
            }
            assertSafeExistingDirectory(current);
        }
    }

    private void ensureDataRoot() {
        try {
            if (!Files.exists(dataDirectory, LinkOption.NOFOLLOW_LINKS)) {
                // The configured root's ancestors are outside application ownership. Once the root exists,
                // every application-owned child is created one level at a time without following links.
                Files.createDirectories(dataDirectory);
            }
        } catch (IOException exception) {
            throw storageError("创建数据目录失败", exception);
        }
        assertSafeExistingDirectory(dataDirectory);
    }

    private void assertSafeExistingDirectory(Path directory) {
        assertInsideDataDirectory(directory);
        assertNoSymbolicLinks(directory);
        try {
            BasicFileAttributes attributes = Files.readAttributes(directory, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()) {
                throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "数据子目录不合法");
            }
            Path realRoot = dataDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path realDirectory = directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realDirectory.startsWith(realRoot)) {
                throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "数据子目录超出数据目录");
            }
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "数据子目录不存在");
        } catch (IOException exception) {
            throw storageError("校验数据目录失败", exception);
        }
    }

    private BasicFileAttributes regularFileAttributes(Path path, BlogErrorCode missingCode, String missingMessage) {
        assertInsideDataDirectory(path);
        assertNoSymbolicLinks(path);
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw new BlogException(BlogErrorCode.INVALID_FILE_NAME, "内容项不是普通文件");
            }
            return attributes;
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new BlogException(missingCode, missingMessage);
        } catch (IOException exception) {
            throw storageError("读取文件属性失败", exception);
        }
    }

    private void assertNoSymbolicLinks(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        assertInsideDataDirectory(normalized);
        if (Files.isSymbolicLink(dataDirectory)) {
            throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "数据目录不能是符号链接");
        }
        Path current = dataDirectory;
        Path relative = dataDirectory.relativize(normalized);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "不允许读取符号链接");
            }
        }
    }

    private void assertInsideDataDirectory(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(dataDirectory)) {
            throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "文件路径超出数据目录");
        }
    }

    private void atomicWrite(Path target, byte[] bytes, boolean replaceExisting, String expectedTargetVersion) {
        assertInsideDataDirectory(target);
        ensureDirectory(target.getParent());
        Path temp = null;
        try {
            temp = Files.createTempFile(target.getParent(), ".speaive-", ".tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            atomicMove(temp, target, replaceExisting, null, expectedTargetVersion, true);
            if (!Files.exists(temp, LinkOption.NOFOLLOW_LINKS)) {
                temp = null;
            }
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            throw new BlogException(BlogErrorCode.SLUG_CONFLICT,
                    "slug 已存在：" + target.getFileName().toString().replaceFirst("\\.md$", ""));
        } catch (IOException exception) {
            throw storageError("写入文件失败", exception);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // The next data-directory cleanup can remove an abandoned temporary file.
                }
            }
        }
    }

    private void atomicMove(
            Path source,
            Path target,
            boolean replaceExisting,
            String expectedSourceVersion,
            String expectedTargetVersion,
            boolean sourceResidueIsHarmless) {
        assertInsideDataDirectory(source);
        assertInsideDataDirectory(target);
        assertSafeExistingDirectory(source.getParent());
        assertSafeExistingDirectory(target.getParent());
        beforeFileCommit.beforeCommit(source, target, replaceExisting);
        assertNoSymbolicLinks(source);
        assertNoSymbolicLinks(target);
        if (expectedSourceVersion != null) {
            assertPathVersion(source, expectedSourceVersion);
        }
        if (expectedTargetVersion != null) {
            assertPathVersion(target, expectedTargetVersion);
        }
        // Recheck both parents after hashing so a static symlink cannot become the move destination.
        assertSafeExistingDirectory(source.getParent());
        assertSafeExistingDirectory(target.getParent());
        try {
            if (replaceExisting) {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                installWithoutReplacing(source, target, sourceResidueIsHarmless);
            }
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            throw new BlogException(BlogErrorCode.SLUG_CONFLICT, "目标文件已经存在");
        } catch (SourceCleanupException exception) {
            throw storageError(exception.getMessage(), exception);
        } catch (IOException exception) {
            throw storageError("移动文件失败", exception);
        }
    }

    private void installWithoutReplacing(Path source, Path target, boolean sourceResidueIsHarmless)
            throws IOException {
        try {
            Files.createLink(target, source);
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            throw exception;
        } catch (UnsupportedOperationException | IOException linkException) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new java.nio.file.FileAlreadyExistsException(target.toString());
            }
            try {
                // Without ATOMIC_MOVE, the provider must honor the standard no-replace contract.
                Files.move(source, target);
                return;
            } catch (IOException moveException) {
                moveException.addSuppressed(linkException);
                throw moveException;
            }
        }

        try {
            Files.delete(source);
        } catch (IOException cleanupException) {
            if (!sourceResidueIsHarmless) {
                throw new SourceCleanupException(
                        "目标文件已创建，但源文件清理失败；为避免数据丢失已保留两份", cleanupException);
            }
            // Atomic writes use a hidden temporary source. The installed target is complete and authoritative;
            // the outer finally block retries cleanup and scanners ignore any remaining temporary link.
        }
    }

    private byte[] readBytes(Path path, long maxBytes) {
        assertInsideDataDirectory(path);
        assertSafeExistingDirectory(path.getParent());
        assertNoSymbolicLinks(path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            assertSafeExistingDirectory(path.getParent());
            long size = channel.size();
            if (size > maxBytes || size > Integer.MAX_VALUE) {
                throw new BlogException(BlogErrorCode.TOO_LARGE, "文件超过大小限制");
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // Keep reading until the file size observed at open time has been consumed.
            }
            if (channel.size() != size) {
                throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "文件在读取期间发生变化，请重试");
            }
            return java.util.Arrays.copyOf(buffer.array(), buffer.position());
        } catch (IOException exception) {
            throw storageError("读取文件失败", exception);
        }
    }

    private void assertPathVersion(Path path, String expectedVersion) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw versionConflict();
        }
        BasicFileAttributes attributes = regularFileAttributes(path, BlogErrorCode.NOT_FOUND, "文章不存在");
        assertMarkdownSize(attributes.size());
        String actualVersion = sha256(readBytes(path, maxMarkdownBytes));
        if (!actualVersion.equals(expectedVersion)) {
            throw versionConflict();
        }
    }

    private void assertMarkdownSize(long size) {
        if (size > maxMarkdownBytes) {
            throw new BlogException(BlogErrorCode.TOO_LARGE,
                    "Markdown 不能超过 " + maxMarkdownBytes + " 字节");
        }
    }

    private static void requireVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "version 不能为空");
        }
    }

    private static void assertVersion(Post post, String expectedVersion) {
        if (!post.version().equals(expectedVersion)) {
            throw versionConflict();
        }
    }

    private static BlogException versionConflict() {
        return new BlogException(BlogErrorCode.VERSION_CONFLICT,
                "文章已被其他操作修改，请刷新后重试");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void validatePlainFileName(String name, String requiredExtension, String errorMessage) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0
                || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME, errorMessage);
        }
        if (requiredExtension != null && !name.endsWith(requiredExtension)) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME, errorMessage);
        }
    }

    private static boolean existsNoFollow(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private <T> T withLock(String slug, Supplier<T> action) {
        return locks.withLock(slug, action);
    }

    private static BlogException storageError(String message, IOException cause) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message, cause);
    }

    private static List<Post> concat(List<Post> first, List<Post> second) {
        List<Post> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private record StoredPost(Path path, Post post) {
    }

    private record ScanResult(List<Post> posts, List<ContentError> errors) {
    }

    private static final class SourceCleanupException extends IOException {
        private SourceCleanupException(String message, IOException cause) {
            super(message, cause);
        }
    }

}
