package com.speaive.blog.application.result;

import java.time.Instant;
import java.util.List;

public record PostDetailResult(
        String slug,
        String title,
        String description,
        Instant publishedAt,
        Instant updatedAt,
        List<String> tags,
        String cover,
        AuthorResult author,
        String status,
        String body,
        String html,
        String version
) {
    public PostDetailResult {
        tags = List.copyOf(tags);
    }
}
