package com.speaive.blog.interfaces.http.comment;

import java.time.Instant;
import java.util.List;

public final class CommentResponses {
    private CommentResponses() {
    }

    public record CommentDetail(
            String id,
            CommentAuthor author,
            String body,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CommentAuthor(
            String id,
            String username,
            String displayName,
            String type,
            String avatarUrl
    ) {
    }

    public record CommentList(List<CommentDetail> items) {
    }
}
