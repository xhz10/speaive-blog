package com.speaive.blog.domain.creative;

import java.time.Instant;
import java.util.Objects;

public record DiscussionDigest(
        String postId,
        long postRevision,
        String commentsFingerprint,
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant updatedAt
) {
    public DiscussionDigest {
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(commentsFingerprint, "commentsFingerprint");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (postRevision < 1) throw new IllegalArgumentException("postRevision 必须大于 0");
    }
}
