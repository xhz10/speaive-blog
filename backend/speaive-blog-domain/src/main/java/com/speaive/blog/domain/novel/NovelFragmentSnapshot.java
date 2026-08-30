package com.speaive.blog.domain.novel;

import com.speaive.blog.domain.author.Author;

import java.time.Instant;

public record NovelFragmentSnapshot(
        String id,
        NovelFragmentSlug slug,
        NovelFragmentContent content,
        Author author,
        NovelFragmentStatus status,
        NovelFragmentVisibility visibility,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long revision
) {
    public String version() {
        return id + ":" + revision;
    }
}
