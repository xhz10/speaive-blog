package com.speaive.blog.domain.automation;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 可重试的社区自动评论任务，绑定文章 ID、文章修订和角色 ID。数据库原子领取负责进入 RUNNING，本对象负责成功、跳过与失败重试规则。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param postId 所属文章的稳定 ID
 * @param postRevision 生成或快照所依据的文章修订号
 * @param agentId 负责生成或执行任务的 Agent ID
 * @param status 当前业务状态，详见该字段的枚举类型
 * @param attempts 已经领取执行的次数，用于重试上限判断
 * @param availableAt 最早可再次领取执行的时间
 * @param claimedAt 本次任务被领取的时间；等待执行时为空
 * @param completedAt 本次执行结束时间；未结束时为空
 * @param lastError 最近一次错误或跳过原因
 * @param createdAt 首次创建时间
 * @param updatedAt 最近一次修改或状态变化时间
 */
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
