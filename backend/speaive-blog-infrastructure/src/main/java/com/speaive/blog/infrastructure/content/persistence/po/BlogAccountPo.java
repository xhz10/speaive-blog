package com.speaive.blog.infrastructure.content.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("blog_account")
public class BlogAccountPo {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("password_hash")
    private String passwordHash;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    /** 会员身份，READER 普通会员，WRITER 作者。 */
    @TableField("role")
    private String role;
    /** 是否允许公开发布。 */
    @TableField("can_publish")
    private boolean canPublish;
    /** 管理员是否开放加密资格。 */
    @TableField("encryption_allowed")
    private boolean encryptionAllowed;
    /** 作者选择的加密存储开关。 */
    @TableField("content_encrypted")
    private boolean contentEncrypted;
    /** 账号设置并发版本。 */
    @TableField("settings_version")
    private long settingsVersion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean getCanPublish() { return canPublish; }
    public void setCanPublish(boolean canPublish) { this.canPublish = canPublish; }
    public boolean getEncryptionAllowed() { return encryptionAllowed; }
    public void setEncryptionAllowed(boolean encryptionAllowed) { this.encryptionAllowed = encryptionAllowed; }
    public boolean getContentEncrypted() { return contentEncrypted; }
    public void setContentEncrypted(boolean contentEncrypted) { this.contentEncrypted = contentEncrypted; }
    public long getSettingsVersion() { return settingsVersion; }
    public void setSettingsVersion(long settingsVersion) { this.settingsVersion = settingsVersion; }
}
