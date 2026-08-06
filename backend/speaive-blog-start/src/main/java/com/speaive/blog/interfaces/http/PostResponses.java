package com.speaive.blog.interfaces.http;

import java.time.Instant;
import java.util.List;

final class PostResponses {
    private PostResponses() {
    }

    record PostDetail(
            String slug,
            String title,
            String description,
            Instant publishedAt,
            Instant updatedAt,
            List<String> tags,
            String cover,
            AuthorSummary author,
            String status,
            String body,
            String html,
            String version
    ) {
    }

    record PostSummary(
            String slug,
            String title,
            String description,
            Instant publishedAt,
            Instant updatedAt,
            List<String> tags,
            String cover,
            AuthorSummary author,
            String status,
            String version
    ) {
    }

    record AuthorSummary(String id, String username, String displayName, String type, String avatarUrl) {
    }

    record ContentScanError(String file, String status, String message) {
    }

    record PostList(List<PostSummary> items, List<ContentScanError> errors) {
    }

    record StoredMediaResponse(String url, String relativePath, String mimeType, long size) {
    }
}
