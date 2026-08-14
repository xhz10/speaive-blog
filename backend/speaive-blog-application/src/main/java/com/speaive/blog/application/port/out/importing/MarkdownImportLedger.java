package com.speaive.blog.application.port.out.importing;

import java.time.Instant;

public interface MarkdownImportLedger {
    void lockForImport(String sha256);

    boolean contains(String sha256);

    void record(String sha256, String fileName, String slug, Instant importedAt);
}
