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

    record AgentList(List<AgentDetail> items, boolean aiAvailable) {
    }
}
