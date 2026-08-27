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
import java.util.Optional;

public final class PostgresAccountRepository implements AccountRepository {
    private final BlogAccountDatabaseMapper database;
    private final BlogAuthorDatabaseMapper authors;
    private final BlogAiPersistenceMapStructMapper mapping;

    public PostgresAccountRepository(
            BlogAccountDatabaseMapper database,
            BlogAuthorDatabaseMapper authors,
            BlogAiPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
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
        BlogAccountPo po = new BlogAccountPo();
        po.setId(account.id());
        po.setPasswordHash(account.passwordHash());
        po.setCreatedAt(account.createdAt());
        po.setUpdatedAt(account.updatedAt());
        try {
            if (authors.insertMemberAuthor(mapping.toAuthorPo(account.identity()), account.createdAt()) != 1
                    || database.insert(po) != 1) {
                throw storage("创建会员账号失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "用户名已经存在", exception);
        }
    }

    private MemberAccount rehydrate(BlogAccountPo account) {
        var author = authors.selectById(account.getId());
        if (author == null) {
            throw storage("会员账号关联的用户身份不存在");
        }
        return MemberAccount.rehydrate(mapping.toAuthor(author), account.getPasswordHash(),
                account.getCreatedAt(), account.getUpdatedAt());
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
