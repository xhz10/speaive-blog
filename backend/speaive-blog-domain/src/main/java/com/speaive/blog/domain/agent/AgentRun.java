package com.speaive.blog.domain.agent;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 单次 AI 生成的审计记录，绑定文章修订、角色提示词版本和可选回复目标。生成成功只意味着产生待审核评论。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param postId 所属文章的稳定 ID
 * @param postRevision 生成或快照所依据的文章修订号
 * @param agentId 负责生成或执行任务的 Agent ID
 * @param promptVersion 本次调用使用的角色提示词版本
 * @param model 实际或指定的模型名称；未提供时可为空
 * @param targetCommentId 本次生成的回复目标；根评论生成时为空
 * @param status 当前业务状态，详见该字段的枚举类型
 * @param commentId 成功生成的评论 ID；完成前可为空
 * @param inputTokens 服务商返回的输入 token 数；为空表示未提供，不能当作零
 * @param outputTokens 服务商返回的输出 token 数；为空表示未提供，不能当作零
 * @param errorMessage 失败原因摘要；成功或尚未失败时可为空
 * @param startedAt 本次模型调用登记开始的时间
 * @param completedAt 本次执行结束时间；未结束时为空
 */
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
