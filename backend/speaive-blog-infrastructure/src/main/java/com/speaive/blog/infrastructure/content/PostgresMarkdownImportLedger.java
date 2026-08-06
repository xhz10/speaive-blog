package com.speaive.blog.infrastructure.content;

import com.speaive.blog.application.BlogErrorCode;
import com.speaive.blog.application.BlogException;
import com.speaive.blog.application.port.out.MarkdownImportLedger;

import java.time.Instant;
import java.util.Objects;

public final class PostgresMarkdownImportLedger implements MarkdownImportLedger {
    private final MarkdownImportDatabaseMapper database;

    public PostgresMarkdownImportLedger(MarkdownImportDatabaseMapper database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public void lockForImport(String sha256) {
        database.lockByHash(sha256);
    }

    @Override
    public boolean contains(String sha256) {
        return database.countByHash(sha256) > 0;
    }

    @Override
    public void record(String sha256, String fileName, String slug, Instant importedAt) {
        if (database.insert(sha256, fileName, slug, importedAt) != 1) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "写入 Markdown 导入记录失败");
        }
    }
}
