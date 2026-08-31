package com.speaive.blog.application.result.creative;

import java.time.Instant;

public record ShareGrantResult(
        String id,
        String contentType,
        String contentSlug,
        String token,
        Instant expiresAt,
        Instant revokedAt,
        Instant lastAccessedAt,
        Instant createdAt,
        boolean active
) {
}
