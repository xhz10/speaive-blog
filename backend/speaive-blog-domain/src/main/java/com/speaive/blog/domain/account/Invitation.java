package com.speaive.blog.domain.account;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

public record Invitation(
        String id,
        String codeHash,
        int maxUses,
        int usedCount,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
    public Invitation {
        id = requireText(id, "邀请码 ID 不能为空", 36);
        codeHash = requireText(codeHash, "邀请码哈希不能为空", 64);
        if (maxUses < 1 || maxUses > 100 || usedCount < 0 || usedCount > maxUses) {
            throw invalid("邀请码使用次数无效");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(createdAt) || updatedAt.isBefore(createdAt)) {
            throw invalid("邀请码时间范围无效");
        }
    }

    public static Invitation create(
            String id, String codeHash, int maxUses, Instant expiresAt, Instant now) {
        return new Invitation(id, codeHash, maxUses, 0, expiresAt, now, now);
    }

    public boolean usableAt(Instant now) {
        return usedCount < maxUses && now.isBefore(expiresAt);
    }

    public Invitation consume(Instant now) {
        if (!usableAt(now)) {
            throw invalid("邀请码无效、已用完或已经过期");
        }
        return new Invitation(id, codeHash, maxUses, usedCount + 1, expiresAt, createdAt, now);
    }

    private static String requireText(String value, String message, int expectedLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() != expectedLength) {
            throw invalid(message);
        }
        return normalized;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_ACCOUNT, message);
    }
}
