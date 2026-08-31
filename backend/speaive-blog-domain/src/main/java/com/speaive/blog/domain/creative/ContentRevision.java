package com.speaive.blog.domain.creative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ContentRevision(
        String contentId,
        CreativeContentType contentType,
        long revision,
        String eventType,
        String slug,
        String title,
        String summary,
        String body,
        Instant publishedAt,
        List<String> tags,
        String cover,
        String status,
        String visibility,
        Instant updatedAt,
        Instant recordedAt
) {
    public ContentRevision {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(body, "body");
        tags = tags == null ? List.of() : List.copyOf(tags);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (revision < 1) throw new IllegalArgumentException("revision 必须大于 0");
    }
}
