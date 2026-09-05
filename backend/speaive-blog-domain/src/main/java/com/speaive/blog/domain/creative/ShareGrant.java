package com.speaive.blog.domain.creative;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 临时分享授权，保存内容引用、令牌哈希、有效期与撤销时间。原始令牌只在创建接口返回，访问时必须仍未撤销且未过期。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param contentType 引用内容的业务类型：文章或小说片段
 * @param contentSlug 被引用内容的路径标识
 * @param tokenHash 分享令牌的 SHA-256 哈希；原始令牌只向创建者返回一次
 * @param expiresAt 过期时间，到达此时刻即不可再使用
 * @param revokedAt 撤销时间；为空表示尚未撤销
 * @param lastAccessedAt 最近一次成功访问时间；从未访问时为空
 * @param createdAt 首次创建时间
 */
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
