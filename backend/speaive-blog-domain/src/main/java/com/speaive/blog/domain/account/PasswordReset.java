package com.speaive.blog.domain.account;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import java.time.Instant;
import java.util.Objects;

/** 一次性密码找回凭证，只持有令牌哈希并绑定签发时的账号凭证版本。 */
public record PasswordReset(String tokenHash, String accountId, long credentialVersion,
        Instant expiresAt, Instant createdAt, Instant consumedAt) {
    public PasswordReset {
        if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}") || accountId == null || accountId.isBlank()
                || credentialVersion < 1) throw invalid();
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt) || (consumedAt != null && consumedAt.isBefore(createdAt))) throw invalid();
    }

    /** 已消费、到期、停用或密码已经变更时统一拒绝，避免凭证状态泄漏。 */
    public PasswordReset consume(MemberAccount account, Instant now) {
        if (consumedAt != null || !now.isBefore(expiresAt) || !account.enabled()
                || !accountId.equals(account.id()) || credentialVersion != account.credentialVersion()) throw invalid();
        return new PasswordReset(tokenHash, accountId, credentialVersion, expiresAt, createdAt, now);
    }

    private static DomainException invalid() {
        return new DomainException(DomainErrorCode.INVALID_ACCOUNT, "找回链接无效或已过期，请联系站长重新生成");
    }
}
