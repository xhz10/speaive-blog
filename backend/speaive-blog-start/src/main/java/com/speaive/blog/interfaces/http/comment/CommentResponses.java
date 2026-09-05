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
    /** 按选择顺序返回每个角色的成功评论或错误，允许部分成功。 */
    public record CommentBatch(List<CommentBatchItem> items) {}

    /** 成功时 comment 非空；失败时显示 errorMessage，并保留角色供重试。 */
    public record CommentBatchItem(String agentId, CommentDetail comment, String errorCode, String errorMessage) {}
}
