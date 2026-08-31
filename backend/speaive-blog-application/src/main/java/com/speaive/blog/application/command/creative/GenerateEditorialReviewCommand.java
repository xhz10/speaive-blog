package com.speaive.blog.application.command.creative;

public record GenerateEditorialReviewCommand(
        String agentId,
        String quoteText,
        String quotePrefix,
        String quoteSuffix
) {
}
