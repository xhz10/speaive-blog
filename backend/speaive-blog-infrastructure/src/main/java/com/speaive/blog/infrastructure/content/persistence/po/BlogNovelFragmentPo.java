package com.speaive.blog.infrastructure.content.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("blog_novel_fragment")
public class BlogNovelFragmentPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String slug;
    private String title;
    private String excerpt;
    private String body;
    @TableField("author_id")
    private String authorId;
    private NovelFragmentStatusPo status;
    private NovelFragmentVisibilityPo visibility;
    @TableField("published_at")
    private Instant publishedAt;
    private long revision;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;

    public BlogNovelFragmentPo() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public NovelFragmentStatusPo getStatus() { return status; }
    public void setStatus(NovelFragmentStatusPo status) { this.status = status; }
    public NovelFragmentVisibilityPo getVisibility() { return visibility; }
    public void setVisibility(NovelFragmentVisibilityPo visibility) { this.visibility = visibility; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
