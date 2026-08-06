package com.speaive.blog.domain;

import java.time.Instant;
import java.util.List;

public record PostSummary(
        String id,
        PostSlug slug,
        String title,
        String description,
        Instant publishedAt,
        Instant updatedAt,
        List<String> tags,
        String cover,
        Author author,
        PostStatus status,
        long revision
) {
    public PostSummary {
        PostContent normalized = new PostContent(title, description, publishedAt, tags, cover, "");
        if (id == null || id.isBlank() || id.indexOf(':') >= 0 || slug == null || author == null || status == null
                || updatedAt == null || revision < 1) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "文章摘要缺少必要字段");
        }
        id = id.trim();
        title = normalized.title();
        description = normalized.description();
        tags = normalized.tags();
        cover = normalized.cover();
    }

    public String slugText() {
        return slug.value();
    }

    public String version() {
        return id + ":" + revision;
    }
}
