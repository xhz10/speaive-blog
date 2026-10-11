package com.speaive.blog.domain.account;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 邀请码的使用配额与有效期。领取资格由领域对象检查，应用层配合数据库锁原子消费次数。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param codeHash 邀请码的单向哈希，不保存原始邀请码
 * @param maxUses 邀请码允许使用的总次数
 * @param usedCount 已经消费的邀请次数
 * @param expiresAt 过期时间，到达此时刻即不可再使用
 * @param createdAt 首次创建时间
 * @param updatedAt 最近一次修改或状态变化时间
 * @param note 站长的可选管理备注，最多 200 个字符
 * @param revokedAt 撤销时间，为空表示尚未撤销
 */
public record Invitation(
        String id,
        String codeHash,
        int maxUses,
        int usedCount,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        String note,
        Instant revokedAt
) {
    public Invitation {
        id = requireText(id, "邀请码 ID 不能为空", 36);
        codeHash = requireText(codeHash, "邀请码哈希不能为空", 64);
        if (maxUses < 1 || maxUses > 100 || usedCount < 0 || usedCount > maxUses) {
            throw invalid("邀请码使用次数无效");
        }
        note = note == null ? null : note.trim();
        if (note != null && note.length() > 200) throw invalid("邀请码备注不能超过 200 个字符");
        if (note != null && note.isEmpty()) note = null;
        if (revokedAt != null && revokedAt.isBefore(createdAt)) throw invalid("撤销时间不能早于创建时间");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(createdAt) || updatedAt.isBefore(createdAt)) {
            throw invalid("邀请码时间范围无效");
        }
    }

    public static Invitation create(
            String id, String codeHash, int maxUses, Instant expiresAt, Instant now) {
        return create(id, codeHash, maxUses, expiresAt, now, null);
    }

    public static Invitation create(String id, String codeHash, int maxUses, Instant expiresAt, Instant now, String note) {
        return new Invitation(id, codeHash, maxUses, 0, expiresAt, now, now, note, null);
    }

    /** 撤销幂等，不清除备注、已使用次数或已经注册的会员。 */
    public Invitation revoke(Instant now) {
        return revokedAt != null ? this : new Invitation(id, codeHash, maxUses, usedCount,
                expiresAt, createdAt, now, note, now);
    }

    public boolean usableAt(Instant now) {
        return revokedAt == null && usedCount < maxUses && now.isBefore(expiresAt);
    }

    public Invitation consume(Instant now) {
        if (revokedAt != null) throw invalid("邀请码已撤销，请联系站长重新生成。");
        if (usedCount >= maxUses) {
            throw invalid("邀请码的使用次数已用完。如果刚刚提交过注册，请先尝试登录。");
        }
        if (!now.isBefore(expiresAt)) {
            throw invalid("邀请码已过期，请联系站长重新生成。");
        }
        return new Invitation(id, codeHash, maxUses, usedCount + 1, expiresAt, createdAt, now, note, revokedAt);
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
