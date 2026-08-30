package com.speaive.blog.domain.novel;

import com.speaive.blog.domain.author.Author;

import java.time.Instant;

public record NovelFragmentSummary(
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
    public String slugText() {
        return slug.value();
    }

    public String title() {
        return content.title();
    }

    public String excerpt() {
        return content.excerpt();
    }

    public String version() {
        return id + ":" + revision;
    }
}
