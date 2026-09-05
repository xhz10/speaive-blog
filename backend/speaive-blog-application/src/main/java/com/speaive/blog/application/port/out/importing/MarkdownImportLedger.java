package com.speaive.blog.application.port.out.importing;

import java.time.Instant;

/**
 * Markdown 导入幂等台账端口；必须在同一事务中按内容哈希串行化，再检查并登记，防止并发重复导入。
 */
public interface MarkdownImportLedger {
    void lockForImport(String sha256);

    boolean contains(String sha256);

    void record(String sha256, String fileName, String slug, Instant importedAt);
}
