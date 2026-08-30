package com.speaive.blog.interfaces.http.novel;

import java.time.Instant;
import java.util.List;

public final class NovelFragmentResponses {
    private NovelFragmentResponses() {
    }

    public record NovelFragmentDetail(
            String slug,
            String title,
            String excerpt,
            Instant publishedAt,
            Instant updatedAt,
            AuthorSummary author,
            String status,
            String visibility,
            String body,
            String html,
            String version
    ) {
    }

    record NovelFragmentSummary(
            String slug,
            String title,
            String excerpt,
            Instant publishedAt,
            Instant updatedAt,
            AuthorSummary author,
            String status,
            String visibility,
            String version
    ) {
    }

    record AuthorSummary(String id, String username, String displayName, String type, String avatarUrl) {
    }

    record NovelFragmentList(List<NovelFragmentSummary> items) {
    }
}
