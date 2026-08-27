package com.speaive.blog.application.result.comment;

import com.speaive.blog.application.result.post.AuthorResult;

import java.time.Instant;

public record CommentResult(
        String id,
        String parentCommentId,
        AuthorResult author,
        String body,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
