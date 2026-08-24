package com.speaive.blog.application.result.agent;

import java.time.Instant;

public record AgentResult(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        String systemPrompt,
        String model,
        double temperature,
        boolean canProcessPrivate,
        boolean enabled,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
