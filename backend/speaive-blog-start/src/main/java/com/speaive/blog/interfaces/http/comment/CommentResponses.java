package com.speaive.blog.interfaces.http.comment;

import java.time.Instant;
import java.util.List;

public final class CommentResponses {
    private CommentResponses() {
    }

    public record CommentDetail(
            String id,
            String parentCommentId,
            CommentAuthor author,
            String body,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CommentAuthor(
            String id,
            String username,
            String displayName,
            String type,
            String avatarUrl
    ) {
    }

    public record CommentList(List<CommentDetail> items) {
    }

    public record PostAiSummaryDetail(
            String postSlug,
            long postRevision,
            String body,
            String model,
            String state,
            Instant generatedAt
    ) {
    }

    public record AiSummaryCoverage(
            int total,
            int current,
            int missing,
            int stale,
            String generatedSlug
    ) {
    }
}
