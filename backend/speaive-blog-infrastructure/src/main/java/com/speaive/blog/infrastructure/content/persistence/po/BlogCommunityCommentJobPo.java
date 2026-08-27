package com.speaive.blog.infrastructure.content.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("blog_community_comment_job")
public class BlogCommunityCommentJobPo {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("post_id")
    private String postId;
    @TableField("post_revision")
    private long postRevision;
    @TableField("agent_id")
    private String agentId;
    private CommunityCommentJobStatusPo status;
    private int attempts;
    @TableField("available_at")
    private Instant availableAt;
    @TableField("claimed_at")
    private Instant claimedAt;
    @TableField("completed_at")
    private Instant completedAt;
    @TableField("last_error")
    private String lastError;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public long getPostRevision() { return postRevision; }
    public void setPostRevision(long postRevision) { this.postRevision = postRevision; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public CommunityCommentJobStatusPo getStatus() { return status; }
    public void setStatus(CommunityCommentJobStatusPo status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
