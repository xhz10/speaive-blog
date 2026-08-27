package com.speaive.blog.infrastructure.content.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("blog_agent")
public class BlogAgentPo {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("owner_account_id")
    private String ownerAccountId;
    @TableField("system_prompt")
    private String systemPrompt;
    private String model;
    private double temperature;
    @TableField("can_process_private")
    private boolean canProcessPrivate;
    @TableField("enabled_requested")
    private boolean enabledRequested;
    @TableField("review_status")
    private AgentReviewStatusPo reviewStatus;
    @TableField("review_note")
    private String reviewNote;
    @TableField("reviewed_at")
    private Instant reviewedAt;
    @TableField("auto_comment_enabled")
    private boolean autoCommentEnabled;
    @TableField("auto_comment_all_posts")
    private boolean autoCommentAllPosts;
    @TableField("prompt_version")
    private long promptVersion;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerAccountId() { return ownerAccountId; }
    public void setOwnerAccountId(String ownerAccountId) { this.ownerAccountId = ownerAccountId; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public boolean isCanProcessPrivate() { return canProcessPrivate; }
    public void setCanProcessPrivate(boolean canProcessPrivate) { this.canProcessPrivate = canProcessPrivate; }
    public boolean isEnabledRequested() { return enabledRequested; }
    public void setEnabledRequested(boolean enabledRequested) { this.enabledRequested = enabledRequested; }
    public AgentReviewStatusPo getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(AgentReviewStatusPo reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public boolean isAutoCommentEnabled() { return autoCommentEnabled; }
    public void setAutoCommentEnabled(boolean autoCommentEnabled) { this.autoCommentEnabled = autoCommentEnabled; }
    public boolean isAutoCommentAllPosts() { return autoCommentAllPosts; }
    public void setAutoCommentAllPosts(boolean autoCommentAllPosts) { this.autoCommentAllPosts = autoCommentAllPosts; }
    public long getPromptVersion() { return promptVersion; }
    public void setPromptVersion(long promptVersion) { this.promptVersion = promptVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
