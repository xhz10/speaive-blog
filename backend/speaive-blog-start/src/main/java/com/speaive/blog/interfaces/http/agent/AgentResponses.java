package com.speaive.blog.interfaces.http.agent;

import java.time.Instant;
import java.util.List;

final class AgentResponses {
    private AgentResponses() {
    }

    record AgentDetail(
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
            List<String> autoCommentTags,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record AgentList(List<AgentDetail> items, boolean aiAvailable) {
    }
}
