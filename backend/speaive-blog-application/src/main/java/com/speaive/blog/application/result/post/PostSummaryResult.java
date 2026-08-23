package com.speaive.blog.application.result.post;

import java.time.Instant;
import java.util.List;

public record PostSummaryResult(
        String slug,
        String title,
        String description,
        Instant publishedAt,
        Instant updatedAt,
        List<String> tags,
        String cover,
        AuthorResult author,
        String status,
        String visibility,
        String version
) {
    public PostSummaryResult {
        tags = List.copyOf(tags);
    }
}
