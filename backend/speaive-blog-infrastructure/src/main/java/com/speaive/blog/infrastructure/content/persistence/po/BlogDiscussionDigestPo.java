package com.speaive.blog.infrastructure.content.persistence.po;

import java.time.Instant;

public class BlogDiscussionDigestPo {
    private String postId;
    private long postRevision;
    private String commentsFingerprint;
    private String body;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Instant updatedAt;

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public long getPostRevision() { return postRevision; }
    public void setPostRevision(long postRevision) { this.postRevision = postRevision; }
    public String getCommentsFingerprint() { return commentsFingerprint; }
    public void setCommentsFingerprint(String commentsFingerprint) { this.commentsFingerprint = commentsFingerprint; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
