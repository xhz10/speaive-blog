package com.speaive.blog.infrastructure.content;

import com.speaive.blog.application.BlogErrorCode;
import com.speaive.blog.application.BlogException;
import com.speaive.blog.domain.MediaContent;
import com.speaive.blog.domain.StoredMedia;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class MediaFileStore {
    private final Path dataDirectory;
    private final Path mediaDirectory;
    private final long maxImageBytes;
    private final Clock clock;
    private final BeforeFileCommit beforeFileCommit;

    MediaFileStore(ContentStorageSettings settings, Clock clock) {
        this(settings, clock, (source, target, replaceExisting) -> {
        });
    }

    MediaFileStore(ContentStorageSettings settings, Clock clock, BeforeFileCommit beforeFileCommit) {
        this.dataDirectory = settings.dataDirectory();
        this.mediaDirectory = dataDirectory.resolve("media");
        this.maxImageBytes = settings.maxImageBytes();
        this.clock = clock;
        this.beforeFileCommit = beforeFileCommit;
        initializeDirectories();
    }

    StoredMedia store(String fileName, String declaredMimeType, byte[] bytes) {
        validatePlainFileName(fileName);
        if (bytes.length == 0) {
            throw new BlogException(BlogErrorCode.INVALID_IMAGE, "图片不能为空");
        }
        if (bytes.length > maxImageBytes) {
            throw new BlogException(BlogErrorCode.TOO_LARGE, "图片不能超过 " + maxImageBytes + " 字节");
        }
        ImageKind kind = ImageKind.detect(bytes)
                .orElseThrow(() -> new BlogException(BlogErrorCode.INVALID_IMAGE,
                        "无法识别图片格式，仅支持 JPG、PNG、GIF、WebP、AVIF"));
        ImageValidator.validate(kind.mimeType(), bytes);
        String extension = extension(fileName).toLowerCase(Locale.ROOT);
        if (!kind.extensions().contains(extension)) {
            throw new BlogException(BlogErrorCode.INVALID_IMAGE,
                    "图片扩展名与实际格式不一致，仅支持 JPG、PNG、GIF、WebP、AVIF");
        }
        if (declaredMimeType != null && !declaredMimeType.isBlank()) {
            String mimeType = declaredMimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!mimeType.equals(kind.mimeType())) {
                throw new BlogException(BlogErrorCode.INVALID_IMAGE, "图片 MIME 类型与实际格式不一致");
            }
        }

        Instant now = clock.instant();
        String year = String.format(Locale.ROOT, "%04d", now.atZone(ZoneOffset.UTC).getYear());
        String month = String.format(Locale.ROOT, "%02d", now.atZone(ZoneOffset.UTC).getMonthValue());
        Path directory = mediaDirectory.resolve(year).resolve(month);
        ensureDirectory(directory);
        String savedName = UUID.randomUUID() + kind.canonicalExtension();
        atomicWrite(directory.resolve(savedName), bytes);
        String relativePath = year + "/" + month + "/" + savedName;
        return new StoredMedia("/media/" + relativePath, relativePath, kind.mimeType(), bytes.length);
    }

    MediaContent read(String relativePath) {
        initializeDirectories();
        Path file = resolveMediaPath(relativePath);
        BasicFileAttributes attributes = regularFileAttributes(file, BlogErrorCode.NOT_FOUND, "图片不存在");
        if (attributes.size() > maxImageBytes) {
            throw new BlogException(BlogErrorCode.TOO_LARGE, "图片不能超过 " + maxImageBytes + " 字节");
        }
        byte[] bytes = readBytes(file, maxImageBytes);
        ImageKind kind = ImageKind.detect(bytes)
                .orElseThrow(() -> new BlogException(BlogErrorCode.INVALID_IMAGE, "图片内容与扩展名不一致"));
        ImageValidator.validate(kind.mimeType(), bytes);
        if (!kind.extensions().contains(extension(file.getFileName().toString()).toLowerCase(Locale.ROOT))) {
            throw new BlogException(BlogErrorCode.INVALID_IMAGE, "图片内容与扩展名不一致");
        }
        return new MediaContent(kind.mimeType(), bytes);
    }

    void initializeDirectories() {
        ensureDirectory(dataDirectory);
        ensureDirectory(mediaDirectory);
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
                    throw storageError("创建媒体目录失败", exception);
                }
            }
            assertSafeExistingDirectory(current);
        }
    }

    private void ensureDataRoot() {
        try {
            if (!Files.exists(dataDirectory, LinkOption.NOFOLLOW_LINKS)) {
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
                throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "媒体子目录不合法");
            }
            Path realRoot = dataDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path realDirectory = directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realDirectory.startsWith(realRoot)) {
                throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "媒体子目录超出数据目录");
            }
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "媒体子目录不存在");
        } catch (IOException exception) {
            throw storageError("校验媒体目录失败", exception);
        }
    }

    private BasicFileAttributes regularFileAttributes(Path path, BlogErrorCode missingCode, String missingMessage) {
        assertInsideDataDirectory(path);
        assertNoSymbolicLinks(path);
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw new BlogException(BlogErrorCode.INVALID_FILE_NAME, "媒体项不是普通文件");
            }
            return attributes;
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new BlogException(missingCode, missingMessage);
        } catch (IOException exception) {
            throw storageError("读取媒体文件属性失败", exception);
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

    private Path resolveMediaPath(String input) {
        if (input == null || input.isBlank() || input.indexOf('\0') >= 0 || input.contains("\\")) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME, "图片路径不合法");
        }
        String relativePath = input.startsWith("/media/") ? input.substring(7) : input;
        if (relativePath.startsWith("/") || relativePath.isBlank()) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME, "图片路径不合法");
        }
        String[] segments = relativePath.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "图片路径超出媒体目录");
            }
        }
        Path path = mediaDirectory.resolve(String.join("/", segments)).normalize();
        if (!path.startsWith(mediaDirectory)) {
            throw new BlogException(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, "图片路径超出媒体目录");
        }
        return path;
    }

    private void atomicWrite(Path target, byte[] bytes) {
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
            installWithoutReplacing(temp, target);
            if (!Files.exists(temp, LinkOption.NOFOLLOW_LINKS)) {
                temp = null;
            }
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "媒体文件名冲突，请重试", exception);
        } catch (IOException exception) {
            throw storageError("写入媒体文件失败", exception);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // The next media-directory cleanup can remove an abandoned temporary file.
                }
            }
        }
    }

    private void installWithoutReplacing(Path source, Path target) throws IOException {
        assertInsideDataDirectory(source);
        assertInsideDataDirectory(target);
        assertSafeExistingDirectory(source.getParent());
        assertSafeExistingDirectory(target.getParent());
        beforeFileCommit.beforeCommit(source, target, false);
        assertNoSymbolicLinks(source);
        assertNoSymbolicLinks(target);
        assertSafeExistingDirectory(source.getParent());
        assertSafeExistingDirectory(target.getParent());

        try {
            Files.createLink(target, source);
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            throw exception;
        } catch (UnsupportedOperationException | IOException linkException) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new java.nio.file.FileAlreadyExistsException(target.toString());
            }
            try {
                Files.move(source, target);
                return;
            } catch (IOException moveException) {
                moveException.addSuppressed(linkException);
                throw moveException;
            }
        }
        try {
            Files.delete(source);
        } catch (IOException ignored) {
            // The target is complete; the outer finally block retries cleanup of the hidden source.
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
            return Arrays.copyOf(buffer.array(), buffer.position());
        } catch (IOException exception) {
            throw storageError("读取媒体文件失败", exception);
        }
    }

    private static void validatePlainFileName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.contains("/")
                || name.contains("\\") || name.equals(".") || name.equals("..")
                || name.codePointCount(0, name.length()) > 255) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME, "图片文件名不合法");
        }
    }

    private static String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index);
    }

    private static BlogException storageError(String message, IOException cause) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message, cause);
    }

    private enum ImageKind {
        JPEG(Set.of(".jpg", ".jpeg"), ".jpg", "image/jpeg") {
            @Override
            boolean matches(byte[] bytes) {
                return bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8
                        && unsigned(bytes[2]) == 0xff;
            }
        },
        PNG(Set.of(".png"), ".png", "image/png") {
            private final int[] signature = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

            @Override
            boolean matches(byte[] bytes) {
                return startsWith(bytes, signature);
            }
        },
        GIF(Set.of(".gif"), ".gif", "image/gif") {
            @Override
            boolean matches(byte[] bytes) {
                return ascii(bytes, 0, 6).equals("GIF87a") || ascii(bytes, 0, 6).equals("GIF89a");
            }
        },
        WEBP(Set.of(".webp"), ".webp", "image/webp") {
            @Override
            boolean matches(byte[] bytes) {
                return ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 12).equals("WEBP");
            }
        },
        AVIF(Set.of(".avif"), ".avif", "image/avif") {
            @Override
            boolean matches(byte[] bytes) {
                if (!ascii(bytes, 4, 8).equals("ftyp")) {
                    return false;
                }
                String brands = ascii(bytes, 8, Math.min(bytes.length, 40));
                return brands.contains("avif") || brands.contains("avis");
            }
        };

        private final Set<String> extensions;
        private final String canonicalExtension;
        private final String mimeType;

        ImageKind(Set<String> extensions, String canonicalExtension, String mimeType) {
            this.extensions = extensions;
            this.canonicalExtension = canonicalExtension;
            this.mimeType = mimeType;
        }

        abstract boolean matches(byte[] bytes);

        Set<String> extensions() {
            return extensions;
        }

        String canonicalExtension() {
            return canonicalExtension;
        }

        String mimeType() {
            return mimeType;
        }

        static Optional<ImageKind> detect(byte[] bytes) {
            for (ImageKind kind : values()) {
                if (kind.matches(bytes)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }

        static int unsigned(byte value) {
            return Byte.toUnsignedInt(value);
        }

        static boolean startsWith(byte[] bytes, int[] signature) {
            if (bytes.length < signature.length) {
                return false;
            }
            for (int index = 0; index < signature.length; index++) {
                if (unsigned(bytes[index]) != signature[index]) {
                    return false;
                }
            }
            return true;
        }

        static String ascii(byte[] bytes, int start, int end) {
            if (start < 0 || end < start || bytes.length < end) {
                return "";
            }
            return new String(bytes, start, end - start, java.nio.charset.StandardCharsets.US_ASCII);
        }
    }
}
