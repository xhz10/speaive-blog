package com.speaive.blog.application.result.creative;

import java.time.Instant;

public record InspirationResult(
        String id,
        String title,
        String body,
        String kind,
        String status,
        boolean pinned,
        String targetType,
        String targetSlug,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
