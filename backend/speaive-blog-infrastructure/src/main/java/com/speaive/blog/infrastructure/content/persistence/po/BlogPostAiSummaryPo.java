package com.speaive.blog.infrastructure.content.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("blog_post_ai_summary")
public class BlogPostAiSummaryPo {
    @TableId(value = "post_id", type = IdType.INPUT)
    private String postId;
    @TableField("post_revision")
    private long postRevision;
    private String body;
    private String model;
    @TableField("input_tokens")
    private Integer inputTokens;
    @TableField("output_tokens")
    private Integer outputTokens;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public long getPostRevision() { return postRevision; }
    public void setPostRevision(long postRevision) { this.postRevision = postRevision; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
