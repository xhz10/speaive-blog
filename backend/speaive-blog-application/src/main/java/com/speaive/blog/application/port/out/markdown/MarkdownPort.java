package com.speaive.blog.application.port.out.markdown;

import com.speaive.blog.application.command.post.PostWriteCommand;

import java.time.Instant;
import java.util.Set;

/**
 * Markdown 解析、渲染及媒体引用提取端口。实现可使用解析库和 HTML 清洗器，用例只依赖解析结果。
 */
public interface MarkdownPort {
    ParsedPostDocument parse(MarkdownParseRequest request);

    ParsedPostDocument normalize(PostWriteCommand command, String slug, Instant publishedAt);

    String render(String markdown);

    Set<String> referencedMediaPaths(String body, String cover);
}
