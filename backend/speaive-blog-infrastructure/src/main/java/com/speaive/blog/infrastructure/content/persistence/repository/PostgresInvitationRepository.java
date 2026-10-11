package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.InvitationRepository;
import com.speaive.blog.domain.account.Invitation;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogInvitationDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.AccountRecoveryPersistenceMapStructMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresInvitationRepository implements InvitationRepository {
    private final BlogInvitationDatabaseMapper database;

    private final AccountRecoveryPersistenceMapStructMapper mapping;

    public PostgresInvitationRepository(BlogInvitationDatabaseMapper database, AccountRecoveryPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public List<Invitation> findAll() {
        return database.selectAllInvitations().stream().map(mapping::invitation).toList();
    }

    @Override
    public Optional<Invitation> lockByCodeHash(String codeHash) {
        return Optional.ofNullable(database.lockByCodeHash(codeHash)).map(mapping::invitation);
    }

    @Override
    public Optional<Invitation> lockById(String id) {
        return Optional.ofNullable(database.lockById(id)).map(mapping::invitation);
    }

    @Override
    public void add(Invitation invitation) {
        if (database.insert(mapping.invitation(invitation)) != 1) {
            throw storage("创建邀请码失败");
        }
    }

    @Override
    public void save(Invitation invitation, int expectedUsedCount) {
        if (database.updateUsage(mapping.invitation(invitation), expectedUsedCount) != 1) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "邀请码已被其他注册请求使用");
        }
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
