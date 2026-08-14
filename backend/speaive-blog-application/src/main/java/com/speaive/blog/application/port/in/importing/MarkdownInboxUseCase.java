package com.speaive.blog.application.port.in.importing;

import com.speaive.blog.application.result.importing.MarkdownImportOutcome;

public interface MarkdownInboxUseCase {
    MarkdownImportOutcome importOnce(String fileName, byte[] markdown);
}
