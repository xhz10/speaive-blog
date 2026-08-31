package com.speaive.blog.application.result.creative;

import java.time.Instant;

public record DiscussionDigestResult(
        String postSlug,
        String body,
        String model,
        String state,
        int commentCount,
        Instant updatedAt
) {
}
