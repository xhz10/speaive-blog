package com.speaive.blog.infrastructure.content.persistence.po;

import java.time.Instant;

public class BlogContentRevisionPo {
    private String contentId;
    private long revision;
    private String eventType;
    private String slug;
    private String title;
    private String summary;
    private String body;
    private Instant publishedAt;
    private String cover;
    private String status;
    private String visibility;
    private Instant updatedAt;
    private Instant recordedAt;

    public String getContentId() { return contentId; }
    public void setContentId(String contentId) { this.contentId = contentId; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
