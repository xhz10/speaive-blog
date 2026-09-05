package com.speaive.blog.application.port.in.importing;

import com.speaive.blog.application.result.importing.MarkdownImportOutcome;

/**
 * 文件投递箱导入契约，以内容哈希保证重复投递不会创建重复文章。
 */
public interface MarkdownInboxUseCase {
    MarkdownImportOutcome importOnce(String fileName, byte[] markdown);
}
