package com.speaive.blog.infrastructure.content.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("blog_agent_run")
public class BlogAgentRunPo {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("post_id")
    private String postId;
    @TableField("post_revision")
    private long postRevision;
    @TableField("agent_id")
    private String agentId;
    @TableField("prompt_version")
    private long promptVersion;
    private String model;
    @TableField("target_comment_id")
    private String targetCommentId;
    private AgentRunStatusPo status;
    @TableField("comment_id")
    private String commentId;
    @TableField("input_tokens")
    private Integer inputTokens;
    @TableField("output_tokens")
    private Integer outputTokens;
    @TableField("error_message")
    private String errorMessage;
    @TableField("started_at")
    private Instant startedAt;
    @TableField("completed_at")
    private Instant completedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public long getPostRevision() { return postRevision; }
    public void setPostRevision(long postRevision) { this.postRevision = postRevision; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public long getPromptVersion() { return promptVersion; }
    public void setPromptVersion(long promptVersion) { this.promptVersion = promptVersion; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getTargetCommentId() { return targetCommentId; }
    public void setTargetCommentId(String targetCommentId) { this.targetCommentId = targetCommentId; }
    public AgentRunStatusPo getStatus() { return status; }
    public void setStatus(AgentRunStatusPo status) { this.status = status; }
    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
