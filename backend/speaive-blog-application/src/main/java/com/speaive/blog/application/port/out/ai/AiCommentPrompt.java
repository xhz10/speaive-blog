package com.speaive.blog.application.port.out.ai;

import java.time.Instant;
import java.util.List;

public record AiCommentPrompt(
        String systemPrompt,
        String model,
        double temperature,
        String title,
        String description,
        String body,
        String visibility,
        List<String> tags,
        String aiSummary,
        List<RelatedPost> relatedPosts,
        List<ExistingComment> existingComments,
        ReplyTarget replyTarget,
        String taskInstructions
) {
    public AiCommentPrompt {
        tags = List.copyOf(tags);
        relatedPosts = List.copyOf(relatedPosts);
        existingComments = List.copyOf(existingComments);
    }

    public AiCommentPrompt(
            String systemPrompt,
            String model,
            double temperature,
            String title,
            String description,
            String body,
            String visibility,
            List<String> tags,
            String aiSummary,
            List<RelatedPost> relatedPosts,
            List<ExistingComment> existingComments,
            ReplyTarget replyTarget) {
        this(systemPrompt, model, temperature, title, description, body, visibility, tags, aiSummary,
                relatedPosts, existingComments, replyTarget, null);
    }

    public record RelatedPost(
            String title,
            Instant publishedAt,
            List<String> sharedTags,
            String summary
    ) {
        public RelatedPost {
            sharedTags = List.copyOf(sharedTags);
        }
    }

    public record ExistingComment(
            String id,
            String parentCommentId,
            String author,
            String body,
            Instant createdAt
    ) {
    }

    public record ReplyTarget(String id, String author, String body) {
    }
}
