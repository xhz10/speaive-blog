package com.speaive.blog.interfaces.importing;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.importing.MarkdownInboxUseCase;
import com.speaive.blog.application.result.importing.MarkdownImportOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MarkdownInboxImporter {
    private static final Logger log = LoggerFactory.getLogger(MarkdownInboxImporter.class);
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmssSSS")
            .withZone(ZoneOffset.UTC);

    private final Path inbox;
    private final Path imported;
    private final Path rejected;
    private final long maxMarkdownBytes;
    private final MarkdownInboxUseCase imports;
    private final Clock clock;
    private final AtomicBoolean scanning = new AtomicBoolean();

    public MarkdownInboxImporter(
            Path importDirectory,
            long maxMarkdownBytes,
            MarkdownInboxUseCase imports) {
        this(importDirectory, maxMarkdownBytes, imports, Clock.systemUTC());
    }

    MarkdownInboxImporter(
            Path importDirectory,
            long maxMarkdownBytes,
            MarkdownInboxUseCase imports,
            Clock clock) {
        this.inbox = importDirectory.toAbsolutePath().normalize();
        this.imported = inbox.resolve("imported");
        this.rejected = inbox.resolve("rejected");
        this.maxMarkdownBytes = maxMarkdownBytes;
        this.imports = Objects.requireNonNull(imports, "imports");
        this.clock = clock;
        initializeDirectories();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scanOnStartup() {
        scanNow();
    }

    @Scheduled(fixedDelayString = "${speaive.content.import-scan-interval:30s}")
    public void scanOnSchedule() {
        scanNow();
    }

    public void scanNow() {
        if (!scanning.compareAndSet(false, true)) {
            return;
        }
        try {
            initializeDirectories();
            for (Path file : inboxFiles()) {
                process(file);
            }
        } finally {
            scanning.set(false);
        }
    }

    private void process(Path file) {
        String fileName = file.getFileName().toString();
        try {
            if (!fileName.endsWith(".md")) {
                throw new ImportFailure("仅接受扩展名为小写 .md 的 Markdown 文件");
            }
            byte[] bytes = readRegularFile(file);
            MarkdownImportOutcome outcome = imports.importOnce(fileName, bytes);
            try {
                move(file, imported);
            } catch (IOException exception) {
                // The transaction has committed. Keep the file in inbox so the ledger can
                // identify it as complete and retry only the move on the next scan.
                log.error("Markdown imported but could not be moved from inbox: {}", file, exception);
                return;
            }
            log.info("Markdown inbox {}: {}",
                    outcome == MarkdownImportOutcome.IMPORTED ? "imported" : "deduplicated",
                    fileName);
        } catch (ImportFailure exception) {
            reject(file, exception.getMessage());
        } catch (BlogException exception) {
            if (exception.code() == BlogErrorCode.STORAGE_ERROR) {
                log.error("Markdown inbox database/storage failure; keeping {} for retry", file, exception);
            } else {
                reject(file, exception.getMessage());
            }
        } catch (RetryLater exception) {
            log.info("Markdown inbox item is not stable yet; keeping {} for retry: {}",
                    file, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Markdown inbox infrastructure failure; keeping {} for retry", file, exception);
        }
    }

    private List<Path> inboxFiles() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(inbox)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (entry.equals(imported) || entry.equals(rejected)
                        || name.startsWith(".") || name.endsWith(".uploading") || name.endsWith(".tmp")
                        || Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                files.add(entry);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法扫描 Markdown 导入目录：" + inbox, exception);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }

    private byte[] readRegularFile(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) {
                throw new ImportFailure("导入项必须是普通文件，不能是目录或符号链接");
            }
            if (attributes.size() > maxMarkdownBytes) {
                throw new ImportFailure("Markdown 文件不能超过 " + maxMarkdownBytes + " 字节");
            }
            Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            try (FileChannel channel = FileChannel.open(file, options);
                 ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(attributes.size(), 8192))) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                long total = 0;
                while (channel.read(buffer) >= 0) {
                    buffer.flip();
                    int count = buffer.remaining();
                    total += count;
                    if (total > maxMarkdownBytes) {
                        throw new ImportFailure("Markdown 文件不能超过 " + maxMarkdownBytes + " 字节");
                    }
                    output.write(buffer.array(), buffer.position(), count);
                    buffer.clear();
                }
                byte[] bytes = output.toByteArray();
                BasicFileAttributes afterRead = Files.readAttributes(
                        file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!afterRead.isRegularFile()
                        || attributes.size() != afterRead.size()
                        || !attributes.lastModifiedTime().equals(afterRead.lastModifiedTime())
                        || !Objects.equals(attributes.fileKey(), afterRead.fileKey())) {
                    throw new RetryLater("文件在读取期间发生变化");
                }
                return bytes;
            }
        } catch (ImportFailure exception) {
            throw exception;
        } catch (RetryLater exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RetryLater("暂时无法安全读取导入文件", exception);
        }
    }

    private void reject(Path source, String reason) {
        try {
            Path target = move(source, rejected);
            String diagnostic = "rejectedAt: " + clock.instant() + System.lineSeparator()
                    + "reason: " + sanitizeReason(reason) + System.lineSeparator();
            Files.writeString(target.resolveSibling(target.getFileName() + ".reason.txt"), diagnostic,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            log.warn("Markdown inbox rejected {}: {}", source.getFileName(), sanitizeReason(reason));
        } catch (IOException exception) {
            log.error("Failed to move rejected Markdown inbox item {}", source, exception);
        }
    }

    private Path move(Path source, Path destinationDirectory) throws IOException {
        Path target = availableTarget(destinationDirectory, source.getFileName().toString());
        try {
            return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            return Files.move(source, target);
        }
    }

    private Path availableTarget(Path directory, String fileName) {
        Path direct = directory.resolve(fileName);
        if (!Files.exists(direct, LinkOption.NOFOLLOW_LINKS)) {
            return direct;
        }
        int extension = fileName.lastIndexOf('.');
        String base = extension > 0 ? fileName.substring(0, extension) : fileName;
        String suffix = extension > 0 ? fileName.substring(extension) : "";
        return directory.resolve(base + "-" + FILE_TIMESTAMP.format(clock.instant()) + "-"
                + UUID.randomUUID().toString().substring(0, 8) + suffix);
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(inbox);
            Files.createDirectories(imported);
            Files.createDirectories(rejected);
            assertDirectory(inbox);
            assertDirectory(imported);
            assertDirectory(rejected);
        } catch (IOException exception) {
            throw new IllegalStateException("无法初始化 Markdown 导入目录：" + inbox, exception);
        }
    }

    private static void assertDirectory(Path directory) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || Files.isSymbolicLink(directory)) {
            throw new IOException("导入目录不能是符号链接：" + directory);
        }
    }

    private static String sanitizeReason(String value) {
        String reason = value == null || value.isBlank() ? "未知错误" : value;
        reason = reason.replace('\r', ' ').replace('\n', ' ').trim();
        return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }

    private static final class ImportFailure extends RuntimeException {
        private ImportFailure(String message) {
            super(message);
        }

        private ImportFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class RetryLater extends RuntimeException {
        private RetryLater(String message) {
            super(message);
        }

        private RetryLater(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
