package com.speaive.blog.infrastructure.content.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

/** 一次性找回凭证存储对象，仅保存哈希。 */
@TableName("blog_password_reset")
public class PasswordResetPo {
    @TableId(value = "token_hash", type = IdType.INPUT) private String tokenHash;
    @TableField("account_id") private String accountId;
    @TableField("credential_version") private long credentialVersion;
    @TableField("expires_at") private Instant expiresAt;
    @TableField("created_at") private Instant createdAt;
    @TableField("consumed_at") private Instant consumedAt;
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String value) { tokenHash = value; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String value) { accountId = value; }
    public long getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(long value) { credentialVersion = value; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant value) { expiresAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant value) { consumedAt = value; }
}
