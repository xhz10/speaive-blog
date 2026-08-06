package com.speaive.blog.application.support;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

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
