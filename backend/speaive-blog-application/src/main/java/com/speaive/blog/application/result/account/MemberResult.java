package com.speaive.blog.application.result.account;

import java.time.Instant;

public record MemberResult(
        String id,
        String username,
        String displayName,
        boolean enabled,
        Instant createdAt
) {
}
