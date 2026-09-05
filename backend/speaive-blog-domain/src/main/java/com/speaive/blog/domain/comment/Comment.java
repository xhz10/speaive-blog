package com.speaive.blog.domain.comment;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * 评论聚合根：统一维护 AI 发言、回复关系与审核状态。父评论和文章通过 ID 关联，发布回复时显式校验父评论已公开；新内容始终先待审核。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param postId 所属文章的稳定 ID
 * @param parentCommentId 被回复评论的 ID；根评论为空
 * @param author 内容署名身份的不可变表示
 * @param body 正文内容；格式与长度由当前业务类型约束
 * @param status 当前业务状态，详见该字段的枚举类型
 * @param createdAt 首次创建时间
 * @param updatedAt 最近一次修改或状态变化时间
 */
public record Comment(
        String id,
        String postId,
        String parentCommentId,
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
        parentCommentId = optionalId(parentCommentId);
        if (id.equals(parentCommentId)) {
            throw invalid("评论不能回复自己");
        }
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
        return new Comment(id, postId, null, author, body, CommentStatus.PENDING, now, now);
    }

    public static Comment createAiReplyCandidate(
            String id,
            Comment parent,
            Author author,
            String body,
            Instant now) {
        if (parent == null) {
            throw invalid("不能回复不存在或已隐藏的评论");
        }
        parent.ensureCanReceiveAiReply(parent.postId(), author);
        return new Comment(id, parent.postId(), parent.id(), author, body, CommentStatus.PENDING, now, now);
    }

    /**
     * 校验当前评论能否接受指定 Agent 的回复，不创建评论、不调用模型。
     * 应用层在付费生成前调用，创建回复时再次调用，保证两条路径使用同一份领域规则。
     *
     * @param expectedPostId 本次讨论所属的文章 ID，防止跨文章串楼
     * @param author 准备发言的作者身份，必须是启用且不同于原作者的 Agent
     */
    public void ensureCanReceiveAiReply(String expectedPostId, Author author) {
        if (!postId.equals(expectedPostId)) {
            throw invalid("不能回复这条评论");
        }
        if (status == CommentStatus.HIDDEN) {
            throw invalid("不能回复不存在或已隐藏的评论");
        }
        if (author == null || author.type() != AuthorType.AGENT || !author.canAuthor()) {
            throw invalid("AI 回复必须由启用的 Agent 身份创建");
        }
        if (this.author.id().equals(author.id())) {
            throw invalid("Agent 不能回复自己的评论");
        }
    }

    public Comment publish(Instant now) {
        if (parentCommentId != null) {
            throw invalid("回复必须在父评论发布后才能发布");
        }
        return publishInternal(now);
    }

    public Comment publishReplyTo(Comment parent, Instant now) {
        if (parentCommentId == null || parent == null || !parentCommentId.equals(parent.id())
                || !postId.equals(parent.postId())) {
            throw invalid("回复关联的父评论不合法");
        }
        if (parent.status() != CommentStatus.PUBLISHED) {
            throw invalid("父评论发布后才能发布回复");
        }
        return publishInternal(now);
    }

    private Comment publishInternal(Instant now) {
        if (status == CommentStatus.HIDDEN) {
            throw invalid("已隐藏评论不能直接发布");
        }
        return transition(CommentStatus.PUBLISHED, now);
    }

    public Comment hide(Instant now) {
        return transition(CommentStatus.HIDDEN, now);
    }

    private Comment transition(CommentStatus next, Instant now) {
        return new Comment(id, postId, parentCommentId, author, body, next, createdAt,
                Objects.requireNonNull(now, "now"));
    }

    private static String requireId(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 36) {
            throw invalid(message);
        }
        return normalized;
    }

    private static String optionalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireId(value, "父评论 ID 不合法");
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
