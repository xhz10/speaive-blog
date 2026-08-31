package com.speaive.blog.infrastructure.content.persistence.po;

import java.time.Instant;

public class BlogInspirationPo {
    private String id;
    private String title;
    private String body;
    private String kind;
    private String status;
    private boolean pinned;
    private String targetType;
    private String targetSlug;
    private long revision;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetSlug() { return targetSlug; }
    public void setTargetSlug(String targetSlug) { this.targetSlug = targetSlug; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
