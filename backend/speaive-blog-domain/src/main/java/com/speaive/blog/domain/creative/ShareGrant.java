package com.speaive.blog.domain.creative;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

public record ShareGrant(
        String id,
        CreativeContentType contentType,
        String contentSlug,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        Instant lastAccessedAt,
        Instant createdAt
) {
    public ShareGrant {
        id = require(id, 36, "分享 ID 不合法");
        contentType = Objects.requireNonNull(contentType, "contentType");
        contentSlug = require(contentSlug, 100, "分享内容 slug 不合法");
        tokenHash = require(tokenHash, 64, "分享令牌摘要不合法");
        if (tokenHash.length() != 64) throw invalid("分享令牌摘要不合法");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!expiresAt.isAfter(createdAt)) throw invalid("分享过期时间必须晚于创建时间");
        if (revokedAt != null && revokedAt.isBefore(createdAt)) throw invalid("分享撤销时间不合法");
    }

    public static ShareGrant create(String id, CreativeContentType contentType, String contentSlug,
                                    String tokenHash, Instant expiresAt, Instant now) {
        return new ShareGrant(id, contentType, contentSlug, tokenHash, expiresAt, null, null, now);
    }

    public boolean activeAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public ShareGrant revoke(Instant now) {
        if (revokedAt != null) return this;
        return new ShareGrant(id, contentType, contentSlug, tokenHash, expiresAt, now, lastAccessedAt, createdAt);
    }

    private static String require(String value, int max, String message) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > max) throw invalid(message);
        return result;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_STATE, message);
    }
}
