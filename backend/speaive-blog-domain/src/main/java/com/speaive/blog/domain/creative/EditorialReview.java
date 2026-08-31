package com.speaive.blog.domain.creative;

import java.time.Instant;
import java.util.Objects;

public record EditorialReview(
        String id,
        String postId,
        long postRevision,
        String agentId,
        String quoteText,
        String quotePrefix,
        String quoteSuffix,
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt
) {
    public EditorialReview {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
        if (postRevision < 1) throw new IllegalArgumentException("postRevision 必须大于 0");
    }
}
