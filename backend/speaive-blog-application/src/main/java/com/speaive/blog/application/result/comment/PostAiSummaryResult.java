package com.speaive.blog.application.result.comment;

import java.time.Instant;

public record PostAiSummaryResult(
        String postSlug,
        long postRevision,
        String body,
        String model,
        String state,
        Instant generatedAt
) {
}
