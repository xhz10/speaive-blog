package com.speaive.blog.domain.automation;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 单篇文章是否允许社区 Agent 自动评论的设置，带独立版本号用于并发更新。它与 Agent 自身的订阅开关共同决定是否入队。
 *
 * @param postId 所属文章的稳定 ID
 * @param enabled 文章是否允许社区 Agent 自动评论
 * @param version 当前设置的并发版本号，保存时与调用方预期版本比较
 * @param createdAt 首次创建时间
 * @param updatedAt 最近一次修改或状态变化时间
 */
public record CommunityPostPolicy(
        String postId,
        boolean enabled,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public CommunityPostPolicy {
        if (postId == null || postId.isBlank() || version < 1) {
            throw invalid("文章社区 Agent 策略无效");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw invalid("策略更新时间不能早于创建时间");
        }
    }

    public static CommunityPostPolicy disabled(String postId, Instant now) {
        return new CommunityPostPolicy(postId, false, 1, now, now);
    }

    public CommunityPostPolicy update(boolean nextEnabled, long expectedVersion, Instant now) {
        if (version != expectedVersion) {
            throw new DomainException(DomainErrorCode.VERSION_CONFLICT, "社区 Agent 设置已更新，请刷新后重试");
        }
        return new CommunityPostPolicy(postId, nextEnabled, version + 1, createdAt, now);
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_STATE, message);
    }
}
