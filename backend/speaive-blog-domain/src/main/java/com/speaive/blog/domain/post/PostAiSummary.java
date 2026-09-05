package com.speaive.blog.domain.post;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 与某篇文章修订绑定的 AI 记忆摘要，供后续评论生成复用。文章修订变化后旧摘要仍可保存，但不能被当作当前摘要使用。
 *
 * @param postId 所属文章的稳定 ID
 * @param postRevision 生成或快照所依据的文章修订号
 * @param body 正文内容；格式与长度由当前业务类型约束
 * @param model 实际或指定的模型名称；未提供时可为空
 * @param inputTokens 服务商返回的输入 token 数；为空表示未提供，不能当作零
 * @param outputTokens 服务商返回的输出 token 数；为空表示未提供，不能当作零
 * @param createdAt 首次创建时间
 * @param updatedAt 最近一次修改或状态变化时间
 */
public record PostAiSummary(
        String postId,
        long postRevision,
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt,
        Instant updatedAt
) {
    public static final int MAX_BODY_LENGTH = 1_000;

    public PostAiSummary {
        postId = requireText(postId, "文章 ID 不能为空", 36);
        if (postRevision < 1) {
            throw invalid("文章修订版本必须大于 0");
        }
        body = requireText(body, "AI 摘要不能为空", MAX_BODY_LENGTH);
        model = optionalText(model, 120);
        if (inputTokens != null && inputTokens < 0 || outputTokens != null && outputTokens < 0) {
            throw invalid("Token 用量不能为负数");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw invalid("AI 摘要更新时间不能早于创建时间");
        }
    }

    public static PostAiSummary create(
            String postId,
            long postRevision,
            String body,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            Instant now) {
        return new PostAiSummary(
                postId, postRevision, body, model, inputTokens, outputTokens, now, now);
    }

    public boolean isCurrentFor(Post post) {
        return post != null && postId.equals(post.id()) && postRevision == post.revision();
    }

    private static String requireText(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length == 0 || length > maxLength) {
            throw invalid(length == 0 ? message : message.replace("不能为空", "不能超过 " + maxLength + " 个字符"));
        }
        return normalized;
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid("AI 摘要模型名称过长");
        }
        return normalized;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_CONTENT, message);
    }
}
