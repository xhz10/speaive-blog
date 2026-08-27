package com.speaive.blog.domain.account;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

public final class MemberAccount {
    private static final int MAX_PASSWORD_HASH_LENGTH = 100;

    private final Author identity;
    private final String passwordHash;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MemberAccount(Author identity, String passwordHash, Instant createdAt, Instant updatedAt) {
        this.identity = requireMember(identity);
        this.passwordHash = requirePasswordHash(passwordHash);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw invalid("账号更新时间不能早于创建时间");
        }
    }

    public static MemberAccount register(
            String id, String username, String displayName, String passwordHash, Instant now) {
        Author identity = new Author(
                id, username, displayName, AuthorType.HUMAN, null, AuthorStatus.ACTIVE);
        return new MemberAccount(identity, passwordHash, now, now);
    }

    public static MemberAccount rehydrate(
            Author identity, String passwordHash, Instant createdAt, Instant updatedAt) {
        return new MemberAccount(identity, passwordHash, createdAt, updatedAt);
    }

    public String id() {
        return identity.id();
    }

    public Author identity() {
        return identity;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean enabled() {
        return identity.status() == AuthorStatus.ACTIVE;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static Author requireMember(Author identity) {
        if (identity == null || identity.type() != AuthorType.HUMAN || Author.ADMIN_ID.equals(identity.id())) {
            throw invalid("会员账号必须关联非管理员的人类身份");
        }
        return identity;
    }

    private static String requirePasswordHash(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_PASSWORD_HASH_LENGTH) {
            throw invalid("账号密码哈希无效");
        }
        return normalized;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_ACCOUNT, message);
    }
}
