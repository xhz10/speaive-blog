package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.CommentQueryScope;
import com.speaive.blog.application.port.out.persistence.CommentRepository;
import com.speaive.blog.domain.comment.Comment;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCommentDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogCommentPo;
import com.speaive.blog.infrastructure.content.persistence.po.CommentStatusPo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 评论仓储的 PostgreSQL 适配器，恢复作者与评论领域对象，并用旧状态条件更新审核结果。
 */
public final class PostgresCommentRepository implements CommentRepository {
    private final BlogCommentDatabaseMapper database;
    private final BlogAuthorDatabaseMapper authors;
    private final BlogAiPersistenceMapStructMapper mapping;

    public PostgresCommentRepository(
            BlogCommentDatabaseMapper database,
            BlogAuthorDatabaseMapper authors,
            BlogAiPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public List<Comment> findByPostId(String postId, CommentQueryScope scope) {
        List<BlogCommentPo> rows = switch (scope) {
            case STUDIO -> database.selectAllByPostId(postId);
            case PUBLISHED -> database.selectByPostIdAndStatus(postId, CommentStatusPo.PUBLISHED);
        };
        return rows.stream().map(this::rehydrate).toList();
    }

    @Override
    public Optional<Comment> findById(String id) {
        return Optional.ofNullable(database.selectCommentById(id)).map(this::rehydrate);
    }

    @Override
    public void add(Comment comment) {
        if (database.insert(mapping.toCommentPo(comment)) != 1) {
            throw storage("保存评论失败");
        }
    }

    @Override
    public void save(Comment previous, Comment current) {
        if (!previous.id().equals(current.id())
                || database.updateStatusCas(mapping.toCommentPo(current), mapping.toPo(previous.status())) != 1) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "评论状态已发生变化，请刷新后重试");
        }
    }

    private Comment rehydrate(BlogCommentPo row) {
        BlogAuthorPo author = authors.selectById(row.getAuthorId());
        if (author == null) {
            throw storage("评论关联的作者不存在");
        }
        return new Comment(
                row.getId(), row.getPostId(), row.getParentCommentId(), mapping.toAuthor(author), row.getBody(),
                mapping.toDomain(row.getStatus()), row.getCreatedAt(), row.getUpdatedAt());
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
