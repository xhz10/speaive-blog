package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.AccountRepository;
import com.speaive.blog.domain.account.MemberAccount;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAccountDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAccountPo;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Objects;
import java.util.List;
import com.speaive.blog.domain.account.MemberRole;
import com.speaive.blog.infrastructure.content.persistence.mapping.MemberPersistenceMapStructMapper;
import java.util.Optional;

public final class PostgresAccountRepository implements AccountRepository {
    private final BlogAccountDatabaseMapper database;
    private final BlogAuthorDatabaseMapper authors;
    private final BlogAiPersistenceMapStructMapper mapping;
    private final MemberPersistenceMapStructMapper memberMapping;

    public PostgresAccountRepository(
            BlogAccountDatabaseMapper database,
            BlogAuthorDatabaseMapper authors,
            BlogAiPersistenceMapStructMapper mapping, MemberPersistenceMapStructMapper memberMapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
        this.memberMapping = Objects.requireNonNull(memberMapping, "memberMapping");
    }

    @Override
    public Optional<MemberAccount> findByUsername(String username) {
        return Optional.ofNullable(database.selectByUsername(username)).map(this::rehydrate);
    }

    @Override
    public Optional<MemberAccount> findById(String id) {
        return Optional.ofNullable(database.selectById(id)).map(this::rehydrate);
    }

    @Override
    public Optional<MemberAccount> lockByUsername(String username) {
        return Optional.ofNullable(database.lockByUsername(username)).map(this::rehydrate);
    }

    @Override
    public void add(MemberAccount account) {
        BlogAccountPo po = toPo(account);
        try {
            if (authors.insertMemberAuthor(mapping.toAuthorPo(account.identity()), account.createdAt()) != 1
                    || database.insert(po) != 1) {
                throw storage("创建会员账号失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "用户名已经存在", exception);
        }
    }

    @Override
    public List<MemberAccount> findAll() {
        return database.selectList(null).stream().map(this::rehydrate)
                .sorted(java.util.Comparator.comparing(value -> value.identity().username())).toList();
    }

    @Override
    public void saveSettings(MemberAccount account, long expectedVersion) {
        if (database.updateSettings(toPo(account), expectedVersion) != 1) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "账号设置已更新，请刷新后重试");
        }
    }

    private BlogAccountPo toPo(MemberAccount account) {
        return memberMapping.account(account.id(), account.passwordHash(), account.createdAt(), account.updatedAt(),
                account.role(), account.canPublish(), account.encryptionAllowed(), account.contentEncrypted(), account.settingsVersion());
    }

    private MemberAccount rehydrate(BlogAccountPo account) {
        var author = authors.selectById(account.getId());
        if (author == null) {
            throw storage("会员账号关联的用户身份不存在");
        }
        return MemberAccount.rehydrate(mapping.toAuthor(author), account.getPasswordHash(),
                account.getCreatedAt(), account.getUpdatedAt(), MemberRole.valueOf(account.getRole()),
                account.getCanPublish(), account.getEncryptionAllowed(), account.getContentEncrypted(), account.getSettingsVersion());
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
