package com.speaive.blog.application.result.comment;

public record AiSummaryCoverageResult(
        int total,
        int current,
        int missing,
        int stale,
        String generatedSlug
) {
}
