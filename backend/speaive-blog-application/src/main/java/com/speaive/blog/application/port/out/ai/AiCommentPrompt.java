package com.speaive.blog.application.port.out.ai;

import java.util.List;

public record AiCommentPrompt(
        String systemPrompt,
        String model,
        double temperature,
        String title,
        String description,
        String body,
        String visibility,
        List<ExistingComment> existingComments
) {
    public AiCommentPrompt {
        existingComments = List.copyOf(existingComments);
    }

    public record ExistingComment(String author, String body) {
    }
}
