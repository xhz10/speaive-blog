package com.speaive.blog.application.port.in.markdown;

import com.speaive.blog.application.result.post.PostDetailResult;

public interface MarkdownUseCase {
    PostDetailResult importDraft(String fileName, byte[] markdown);

    String preview(String markdown);
}
