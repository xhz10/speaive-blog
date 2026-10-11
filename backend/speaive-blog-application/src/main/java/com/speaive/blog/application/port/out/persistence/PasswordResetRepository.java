package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.account.PasswordReset;
import java.time.Instant;
import java.util.Optional;

/** 找回凭证持久化端口；所有修改都必须先锁账号，随后再锁令牌，以固定顺序避免死锁。 */
public interface PasswordResetRepository {
    Optional<PasswordReset> findByTokenHash(String tokenHash);
    Optional<PasswordReset> lockByTokenHash(String tokenHash);
    void add(PasswordReset reset);
    void consume(PasswordReset reset);
    void revokeForAccount(String accountId, Instant now);
}
