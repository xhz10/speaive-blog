package com.speaive.blog.domain.account;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 会员账号：关联内容身份与密码哈希。账号注册不赋予站长权限，密码原文不能进入领域对象或日志。
 */
public final class MemberAccount {
    private static final int MAX_PASSWORD_HASH_LENGTH = 100;

    private final Author identity;
    private final String passwordHash;
    /** 管理员授予的业务身份，注册默认普通会员。 */
    private final MemberRole role;
    /** 是否允许公开发布新内容，不能由作者自行授予。 */
    private final boolean canPublish;
    /** 管理员是否允许此账号使用加密存储。 */
    private final boolean encryptionAllowed;
    /** 作者当前选择的文章加密设置；修改时必须原子迁移全部当前内容及历史版本。 */
    private final boolean contentEncrypted;
    /** 账号设置的乐观并发版本，不是文章修订号。 */
    private final long settingsVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MemberAccount(Author identity, String passwordHash, Instant createdAt, Instant updatedAt,
            MemberRole role, boolean canPublish, boolean encryptionAllowed, boolean contentEncrypted, long settingsVersion) {
        this.identity = requireMember(identity);
        this.passwordHash = requirePasswordHash(passwordHash);
        this.role = Objects.requireNonNull(role, "会员身份");
        if (canPublish && role != MemberRole.WRITER) throw invalid("只有作者可以拥有发布权限");
        if (contentEncrypted && !encryptionAllowed) throw invalid("加密存储需要管理员先开放资格");
        if (settingsVersion < 1) throw invalid("账号设置版本不合法");
        this.canPublish = canPublish;
        this.encryptionAllowed = encryptionAllowed;
        this.contentEncrypted = contentEncrypted;
        this.settingsVersion = settingsVersion;
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
        return new MemberAccount(identity, passwordHash, now, now, MemberRole.READER, false, false, false, 1);
    }

    public static MemberAccount rehydrate(
            Author identity, String passwordHash, Instant createdAt, Instant updatedAt) {
        return new MemberAccount(identity, passwordHash, createdAt, updatedAt, MemberRole.READER, false, false, false, 1);
    }

    public static MemberAccount rehydrate(Author identity, String passwordHash, Instant createdAt, Instant updatedAt,
            MemberRole role, boolean canPublish, boolean encryptionAllowed, boolean contentEncrypted, long settingsVersion) {
        return new MemberAccount(identity, passwordHash, createdAt, updatedAt, role, canPublish,
                encryptionAllowed, contentEncrypted, settingsVersion);
    }

    /** 管理员修改资格；不能借撤销资格把已经加密的私密内容自动改成明文。 */
    public MemberAccount changeWritingPermissions(MemberRole nextRole, boolean publish, boolean allowEncryption,
            long expectedVersion, Instant now) {
        assertSettingsVersion(expectedVersion);
        if (contentEncrypted && !allowEncryption) throw invalid("账号正在使用加密，不能直接撤销加密资格");
        return new MemberAccount(identity, passwordHash, createdAt, now, nextRole, publish,
                allowEncryption, contentEncrypted, settingsVersion + 1);
    }

    /** 作者修改存储偏好；应用必须和内容迁移一起提交，避免出现设置与数据库内容不一致。 */
    public MemberAccount chooseContentEncryption(boolean encrypted, long expectedVersion, Instant now) {
        assertSettingsVersion(expectedVersion);
        if (!enabled()) throw invalid("账号已停用");
        if (encrypted && !encryptionAllowed) throw invalid("管理员尚未开放加密资格");
        return new MemberAccount(identity, passwordHash, createdAt, now, role, canPublish,
                encryptionAllowed, encrypted, settingsVersion + 1);
    }

    public void ensureCanWrite() {
        if (!enabled() || role != MemberRole.WRITER) throw invalid("账号尚未开通作者权限");
    }

    public void ensureCanPublish() {
        ensureCanWrite();
        if (!canPublish) throw invalid("账号尚未获得公开发布权限");
    }

    private void assertSettingsVersion(long expected) {
        if (settingsVersion != expected) throw new DomainException(DomainErrorCode.VERSION_CONFLICT, "账号设置已更新，请刷新后重试");
    }

    public MemberRole role() { return role; }
    public boolean canPublish() { return canPublish; }
    public boolean encryptionAllowed() { return encryptionAllowed; }
    public boolean contentEncrypted() { return contentEncrypted; }
    public long settingsVersion() { return settingsVersion; }

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
