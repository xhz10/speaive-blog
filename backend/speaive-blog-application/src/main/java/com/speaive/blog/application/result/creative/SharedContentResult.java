package com.speaive.blog.application.result.creative;

import java.time.Instant;

public record SharedContentResult(
        String contentType,
        String slug,
        String title,
        String summary,
        String body,
        String html,
        Instant publishedAt,
        Instant updatedAt,
        String authorDisplayName,
        Instant expiresAt
) {
}
