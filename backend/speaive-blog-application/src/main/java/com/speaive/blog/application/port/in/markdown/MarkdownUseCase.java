package com.speaive.blog.application.port.in.markdown;

import com.speaive.blog.application.result.post.PostDetailResult;

/**
 * Markdown 预览与人工导入的入站契约。
 */
public interface MarkdownUseCase {
    PostDetailResult importDraft(String fileName, byte[] markdown);

    String preview(String markdown);
}
