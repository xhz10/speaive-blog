package com.speaive.blog.application.result.creative;

import java.time.Instant;
import java.util.List;

public record ContentRevisionResult(
        String contentType,
        long revision,
        String eventType,
        String slug,
        String title,
        String summary,
        String body,
        Instant publishedAt,
        List<String> tags,
        String cover,
        String status,
        String visibility,
        Instant updatedAt,
        Instant recordedAt
) {
    public ContentRevisionResult {
        tags = List.copyOf(tags);
    }
}
