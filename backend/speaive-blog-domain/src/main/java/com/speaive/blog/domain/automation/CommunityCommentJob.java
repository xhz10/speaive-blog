package com.speaive.blog.domain.automation;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record CommunityCommentJob(
        String id,
        String postId,
        long postRevision,
        String agentId,
        CommunityCommentJobStatus status,
        int attempts,
        Instant availableAt,
        Instant claimedAt,
        Instant completedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    private static final int MAX_ERROR_LENGTH = 500;

    public CommunityCommentJob {
        id = requireId(id, "自动评论任务 ID 不能为空");
        postId = requireId(postId, "文章 ID 不能为空");
        agentId = requireId(agentId, "Agent ID 不能为空");
        if (postRevision < 1 || attempts < 0) {
            throw invalid("自动评论任务版本或尝试次数无效");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(availableAt, "availableAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        lastError = normalizeError(lastError);
    }

    public static CommunityCommentJob create(
            String id, String postId, long postRevision, String agentId, Instant now) {
        return new CommunityCommentJob(id, postId, postRevision, agentId,
                CommunityCommentJobStatus.PENDING, 0, now, null, null, null, now, now);
    }

    public CommunityCommentJob succeed(Instant now) {
        requireRunning();
        return finish(CommunityCommentJobStatus.SUCCEEDED, null, now);
    }

    public CommunityCommentJob skip(String reason, Instant now) {
        requireRunning();
        return finish(CommunityCommentJobStatus.SKIPPED, reason, now);
    }

    public CommunityCommentJob failOrRetry(String reason, int maxAttempts, Duration retryDelay, Instant now) {
        requireRunning();
        if (attempts >= maxAttempts) {
            return finish(CommunityCommentJobStatus.FAILED, reason, now);
        }
        return new CommunityCommentJob(id, postId, postRevision, agentId,
                CommunityCommentJobStatus.PENDING, attempts, now.plus(retryDelay), null, null,
                reason, createdAt, now);
    }

    private CommunityCommentJob finish(CommunityCommentJobStatus next, String error, Instant now) {
        return new CommunityCommentJob(id, postId, postRevision, agentId,
                next, attempts, availableAt, claimedAt, now, error, createdAt, now);
    }

    private void requireRunning() {
        if (status != CommunityCommentJobStatus.RUNNING) {
            throw invalid("只有执行中的自动评论任务才能结束");
        }
    }

    private static String requireId(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 36) {
            throw invalid(message);
        }
        return normalized;
    }

    private static String normalizeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_LENGTH);
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_STATE, message);
    }
}
