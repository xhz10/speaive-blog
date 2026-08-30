package com.speaive.blog.application.result.novel;

import com.speaive.blog.application.result.post.AuthorResult;

import java.time.Instant;

public record NovelFragmentSummaryResult(
        String slug,
        String title,
        String excerpt,
        Instant publishedAt,
        Instant updatedAt,
        AuthorResult author,
        String status,
        String visibility,
        String version
) {
}
