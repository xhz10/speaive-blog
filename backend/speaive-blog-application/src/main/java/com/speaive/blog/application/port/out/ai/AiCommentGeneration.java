package com.speaive.blog.application.port.out.ai;

public record AiCommentGeneration(
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens
) {
}
