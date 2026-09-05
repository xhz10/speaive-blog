package com.speaive.blog.application.port.out.markdown;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * 传给 Markdown 适配器的解析输入，携带文件名、正文与相关默认值；不属于 HTTP 请求 DTO。
 */
public record MarkdownParseRequest(String fileName, byte[] markdown, Instant fallbackPublishedAt) {
    public MarkdownParseRequest {
        fileName = Objects.requireNonNull(fileName, "fileName");
        markdown = Arrays.copyOf(Objects.requireNonNull(markdown, "markdown"), markdown.length);
        fallbackPublishedAt = Objects.requireNonNull(fallbackPublishedAt, "fallbackPublishedAt");
    }

    @Override
    public byte[] markdown() {
        return Arrays.copyOf(markdown, markdown.length);
    }
}
