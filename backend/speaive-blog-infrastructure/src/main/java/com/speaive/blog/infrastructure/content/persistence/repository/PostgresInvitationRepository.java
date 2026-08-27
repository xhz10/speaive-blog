package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.InvitationRepository;
import com.speaive.blog.domain.account.Invitation;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogInvitationDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogInvitationPo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresInvitationRepository implements InvitationRepository {
    private final BlogInvitationDatabaseMapper database;

    public PostgresInvitationRepository(BlogInvitationDatabaseMapper database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public List<Invitation> findAll() {
        return database.selectAllInvitations().stream().map(PostgresInvitationRepository::domain).toList();
    }

    @Override
    public Optional<Invitation> lockByCodeHash(String codeHash) {
        return Optional.ofNullable(database.lockByCodeHash(codeHash)).map(PostgresInvitationRepository::domain);
    }

    @Override
    public void add(Invitation invitation) {
        if (database.insert(po(invitation)) != 1) {
            throw storage("创建邀请码失败");
        }
    }

    @Override
    public void save(Invitation invitation, int expectedUsedCount) {
        if (database.updateUsage(po(invitation), expectedUsedCount) != 1) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "邀请码已被其他注册请求使用");
        }
    }

    private static BlogInvitationPo po(Invitation invitation) {
        BlogInvitationPo po = new BlogInvitationPo();
        po.setId(invitation.id());
        po.setCodeHash(invitation.codeHash());
        po.setMaxUses(invitation.maxUses());
        po.setUsedCount(invitation.usedCount());
        po.setExpiresAt(invitation.expiresAt());
        po.setCreatedAt(invitation.createdAt());
        po.setUpdatedAt(invitation.updatedAt());
        return po;
    }

    private static Invitation domain(BlogInvitationPo po) {
        return new Invitation(po.getId(), po.getCodeHash(), po.getMaxUses(), po.getUsedCount(),
                po.getExpiresAt(), po.getCreatedAt(), po.getUpdatedAt());
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
