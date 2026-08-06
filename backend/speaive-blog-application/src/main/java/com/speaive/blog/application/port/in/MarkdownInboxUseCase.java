package com.speaive.blog.application.port.in;

import com.speaive.blog.application.result.MarkdownImportOutcome;

public interface MarkdownInboxUseCase {
    MarkdownImportOutcome importOnce(String fileName, byte[] markdown);
}
