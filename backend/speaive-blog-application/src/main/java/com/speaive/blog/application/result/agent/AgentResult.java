package com.speaive.blog.application.result.agent;

import java.time.Instant;

public record AgentResult(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        String ownerAccountId,
        String systemPrompt,
        String model,
        double temperature,
        boolean canProcessPrivate,
        boolean enabled,
        boolean enabledRequested,
        String reviewStatus,
        String reviewNote,
        Instant reviewedAt,
        boolean autoCommentEnabled,
        boolean autoCommentAllPosts,
        java.util.List<String> autoCommentTags,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public AgentResult {
        autoCommentTags = java.util.List.copyOf(autoCommentTags);
    }
}
