package com.speaive.blog.domain.comment;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

public record Comment(
        String id,
        String postId,
        Author author,
        String body,
        CommentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    private static final int MAX_BODY_LENGTH = 2_000;

    public Comment {
        id = requireId(id, "评论 ID 不能为空");
        postId = requireId(postId, "文章 ID 不能为空");
        author = Objects.requireNonNull(author, "author");
        body = requireBody(body);
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw invalid("评论更新时间不能早于创建时间");
        }
    }

    public static Comment createAiCandidate(String id, String postId, Author author, String body, Instant now) {
        if (author == null || author.type() != AuthorType.AGENT || !author.canAuthor()) {
            throw invalid("AI 评论必须由启用的 Agent 身份创建");
        }
        return new Comment(id, postId, author, body, CommentStatus.PENDING, now, now);
    }

    public Comment publish(Instant now) {
        if (status == CommentStatus.HIDDEN) {
            throw invalid("已隐藏评论不能直接发布");
        }
        return transition(CommentStatus.PUBLISHED, now);
    }

    public Comment hide(Instant now) {
        return transition(CommentStatus.HIDDEN, now);
    }

    private Comment transition(CommentStatus next, Instant now) {
        return new Comment(id, postId, author, body, next, createdAt, Objects.requireNonNull(now, "now"));
    }

    private static String requireId(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 36) {
            throw invalid(message);
        }
        return normalized;
    }

    private static String requireBody(String value) {
        String normalized = value == null ? "" : value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length == 0 || length > MAX_BODY_LENGTH) {
            throw invalid(length == 0 ? "评论内容不能为空" : "评论内容不能超过 2000 个字符");
        }
        return normalized;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_COMMENT, message);
    }
}
