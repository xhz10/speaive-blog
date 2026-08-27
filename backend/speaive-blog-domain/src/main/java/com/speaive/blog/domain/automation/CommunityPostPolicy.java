package com.speaive.blog.domain.automation;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

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
