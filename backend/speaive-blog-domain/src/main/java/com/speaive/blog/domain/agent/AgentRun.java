package com.speaive.blog.domain.agent;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

public record AgentRun(
        String id,
        String postId,
        long postRevision,
        String agentId,
        long promptVersion,
        String model,
        String targetCommentId,
        AgentRunStatus status,
        String commentId,
        Integer inputTokens,
        Integer outputTokens,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
    public AgentRun {
        id = requireId(id, "执行记录 ID 不能为空");
        postId = requireId(postId, "文章 ID 不能为空");
        agentId = requireId(agentId, "Agent ID 不能为空");
        if (postRevision < 1 || promptVersion < 1) {
            throw invalid("文章修订版本和提示词版本必须大于 0");
        }
        model = optional(model, 120);
        targetCommentId = optional(targetCommentId, 36);
        status = Objects.requireNonNull(status, "status");
        commentId = optional(commentId, 36);
        errorMessage = optional(errorMessage, 500);
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        if (inputTokens != null && inputTokens < 0 || outputTokens != null && outputTokens < 0) {
            throw invalid("Token 用量不能为负数");
        }
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw invalid("执行完成时间不能早于开始时间");
        }
    }

    public static AgentRun start(
            String id,
            String postId,
            long postRevision,
            String agentId,
            long promptVersion,
            String model,
            Instant now) {
        return new AgentRun(id, postId, postRevision, agentId, promptVersion, model,
                null, AgentRunStatus.RUNNING, null, null, null, null, now, null);
    }

    public static AgentRun startReply(
            String id,
            String postId,
            long postRevision,
            String agentId,
            long promptVersion,
            String model,
            String targetCommentId,
            Instant now) {
        return new AgentRun(id, postId, postRevision, agentId, promptVersion, model,
                requireId(targetCommentId, "回复目标评论 ID 不能为空"), AgentRunStatus.RUNNING,
                null, null, null, null, now, null);
    }

    public AgentRun succeed(
            String createdCommentId,
            String resolvedModel,
            Integer usedInputTokens,
            Integer usedOutputTokens,
            Instant now) {
        ensureRunning();
        return new AgentRun(id, postId, postRevision, agentId, promptVersion,
                resolvedModel == null ? model : resolvedModel, targetCommentId, AgentRunStatus.SUCCEEDED,
                createdCommentId, usedInputTokens, usedOutputTokens, null, startedAt, now);
    }

    public AgentRun fail(String message, Instant now) {
        ensureRunning();
        String safeMessage = truncate(message == null || message.isBlank() ? "AI 评论生成失败" : message, 500);
        return new AgentRun(id, postId, postRevision, agentId, promptVersion, model, targetCommentId,
                AgentRunStatus.FAILED, null, null, null, safeMessage, startedAt, now);
    }

    private void ensureRunning() {
        if (status != AgentRunStatus.RUNNING) {
            throw invalid("只有执行中的 Agent 任务才能结束");
        }
    }

    private static String requireId(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 36) {
            throw invalid(message);
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid("执行记录字段过长");
        }
        return normalized;
    }

    private static String truncate(String value, int maxLength) {
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_AGENT, message);
    }
}
