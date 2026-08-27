package com.speaive.blog.application.result.account;

import java.time.Instant;

public record InvitationResult(
        String id,
        String code,
        int maxUses,
        int usedCount,
        Instant expiresAt,
        Instant createdAt
) {
}
