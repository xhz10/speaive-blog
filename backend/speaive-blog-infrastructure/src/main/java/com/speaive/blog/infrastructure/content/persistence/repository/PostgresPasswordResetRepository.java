package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.*;
import com.speaive.blog.application.port.out.persistence.PasswordResetRepository;
import com.speaive.blog.domain.account.PasswordReset;
import com.speaive.blog.infrastructure.content.persistence.mapper.PasswordResetDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.AccountRecoveryPersistenceMapStructMapper;
import java.time.Instant;
import java.util.Optional;

/** PostgreSQL 找回凭证适配器，CAS 消费保证同一令牌至多成功一次。 */
public final class PostgresPasswordResetRepository implements PasswordResetRepository {
    private final PasswordResetDatabaseMapper database;
    private final AccountRecoveryPersistenceMapStructMapper mapping;
    public PostgresPasswordResetRepository(PasswordResetDatabaseMapper database, AccountRecoveryPersistenceMapStructMapper mapping) {
        this.database = database; this.mapping = mapping;
    }
    @Override public Optional<PasswordReset> findByTokenHash(String hash) {
        return Optional.ofNullable(database.selectById(hash)).map(mapping::reset);
    }
    @Override public Optional<PasswordReset> lockByTokenHash(String hash) {
        return Optional.ofNullable(database.lockByTokenHash(hash)).map(mapping::reset);
    }
    @Override public void add(PasswordReset reset) {
        if (database.insert(mapping.reset(reset)) != 1) throw new BlogException(BlogErrorCode.STORAGE_ERROR, "保存找回凭证失败");
    }
    @Override public void consume(PasswordReset reset) {
        if (database.consume(mapping.reset(reset)) != 1)
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "找回链接无效或已过期，请联系站长重新生成");
    }
    @Override public void revokeForAccount(String accountId, Instant now) { database.revokeForAccount(accountId, now); }
}
