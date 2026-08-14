package com.speaive.blog.infrastructure.content.media;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.media.MediaContent;
import com.speaive.blog.application.port.out.media.MediaStoragePort;
import com.speaive.blog.application.port.out.media.StoredMedia;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.infrastructure.content.config.ContentStorageSettings;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogMediaDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogMediaPo;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

public final class PostgresMediaStorageAdapter implements MediaStoragePort {
    private static final String HASH_ALGORITHM = "SHA-256";

    private final BlogMediaDatabaseMapper database;
    private final MediaFileStore mediaFiles;
    private final TransactionRunner transactions;
    private final Path mediaDirectory;
    private final Clock clock;

    public PostgresMediaStorageAdapter(
            BlogMediaDatabaseMapper database,
            ContentStorageSettings settings,
            TransactionRunner transactions) {
        this(database, settings, transactions, Clock.systemUTC());
    }

    PostgresMediaStorageAdapter(
            BlogMediaDatabaseMapper database,
            ContentStorageSettings settings,
            TransactionRunner transactions,
            Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.mediaFiles = new MediaFileStore(settings, clock);
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.mediaDirectory = settings.dataDirectory().resolve("media").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public StoredMedia store(String fileName, String declaredMimeType, byte[] bytes) {
        return transactions.required(() -> storeInTransaction(fileName, declaredMimeType, bytes));
    }

    @Override
    public MediaContent read(String relativePath) {
        BlogMediaPo registered = database.selectByPath(relativePath);
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

    private StoredMedia storeInTransaction(String fileName, String declaredMimeType, byte[] bytes) {
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

        BlogMediaPo media = new BlogMediaPo();
        media.setRelativePath(stored.relativePath());
        media.setOriginalFileName(fileName);
        media.setMimeType(stored.mimeType());
        media.setSizeBytes(stored.size());
        media.setSha256(sha256(bytes));
        media.setCreatedAt(clock.instant());
        try {
            if (database.insert(media) != 1) {
                throw new BlogException(BlogErrorCode.STORAGE_ERROR, "写入媒体记录失败");
            }
        } catch (RuntimeException exception) {
            if (!synchronizedTransaction) {
                deleteMediaFile(stored.relativePath());
            }
            if (exception instanceof BlogException blogException) {
                throw blogException;
            }
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "写入媒体记录失败", exception);
        }
        return stored;
    }

    private void deleteMediaFile(String relativePath) {
        Path target = mediaDirectory.resolve(relativePath).normalize();
        if (!target.startsWith(mediaDirectory)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // An unregistered file is never exposed by read().
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(HASH_ALGORITHM + " is not available", exception);
        }
    }
}
