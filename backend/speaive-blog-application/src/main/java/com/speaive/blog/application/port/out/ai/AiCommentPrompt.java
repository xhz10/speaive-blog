package com.speaive.blog.application.port.out.ai;

import java.time.Instant;
import java.util.List;

/**
 * 用例组装并授权的 AI 输入快照，包含文章、相关摘要、已有讨论和可选回复目标；它不是直接来自 HTTP 的模型请求。
 */
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
