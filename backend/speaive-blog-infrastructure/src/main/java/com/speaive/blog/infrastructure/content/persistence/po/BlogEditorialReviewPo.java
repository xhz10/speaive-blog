package com.speaive.blog.infrastructure.content.persistence.po;

import java.time.Instant;

public class BlogEditorialReviewPo {
    private String id;
    private String postId;
    private long postRevision;
    private String agentId;
    private String quoteText;
    private String quotePrefix;
    private String quoteSuffix;
    private String body;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }
    public long getPostRevision() { return postRevision; }
    public void setPostRevision(long postRevision) { this.postRevision = postRevision; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getQuoteText() { return quoteText; }
    public void setQuoteText(String quoteText) { this.quoteText = quoteText; }
    public String getQuotePrefix() { return quotePrefix; }
    public void setQuotePrefix(String quotePrefix) { this.quotePrefix = quotePrefix; }
    public String getQuoteSuffix() { return quoteSuffix; }
    public void setQuoteSuffix(String quoteSuffix) { this.quoteSuffix = quoteSuffix; }
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
}
