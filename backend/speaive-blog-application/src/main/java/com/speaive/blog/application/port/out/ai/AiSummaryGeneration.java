package com.speaive.blog.application.port.out.ai;

public record AiSummaryGeneration(
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens
) {
}
