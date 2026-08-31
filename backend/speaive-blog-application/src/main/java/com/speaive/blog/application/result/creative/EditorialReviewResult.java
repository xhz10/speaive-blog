package com.speaive.blog.application.result.creative;

import java.time.Instant;

public record EditorialReviewResult(
        String id,
        long postRevision,
        String agentId,
        String agentDisplayName,
        String quoteText,
        String quotePrefix,
        String quoteSuffix,
        String body,
        String model,
        Instant createdAt,
        boolean stale
) {
}
