package com.speaive.blog.application.port.out.ai;

import java.util.List;

public record AiSummaryPrompt(
        String title,
        String description,
        List<String> tags,
        String body
) {
    public AiSummaryPrompt {
        tags = List.copyOf(tags);
    }
}
