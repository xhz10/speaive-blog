package com.speaive.blog.domain;

import java.time.Instant;
import java.util.List;

public record Post(
        String slug,
        String title,
        String description,
        Instant publishedAt,
        Instant updatedAt,
        List<String> tags,
        String cover,
        Author author,
        PostStatus status,
        String body,
        String html,
        String version
) {
    public Post {
        tags = List.copyOf(tags);
        author = java.util.Objects.requireNonNull(author, "author");
    }
}
