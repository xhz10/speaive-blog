package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.persistence.ArchiveReceipt;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostChange;
import com.speaive.blog.domain.post.PostRevisionEventType;
import com.speaive.blog.domain.post.PostSummary;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogPostDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogPostPo;
import com.speaive.blog.infrastructure.content.persistence.po.PostStatusPo;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PostgresPostRepository implements PostRepository {
    private static final String DATABASE_ARCHIVE_PREFIX = "database:";

    private final BlogPostDatabaseMapper database;
    private final BlogAuthorDatabaseMapper authors;
    private final BlogPersistenceMapStructMapper mapping;

    public PostgresPostRepository(
            BlogPostDatabaseMapper database,
            BlogAuthorDatabaseMapper authors,
            BlogPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public List<PostSummary> findAll(PostQueryScope scope) {
        List<BlogPostPo> rows = switch (Objects.requireNonNull(scope, "scope")) {
            case STUDIO -> database.selectAllSummaries();
            case PUBLISHED -> database.selectSummariesByStatus(PostStatusPo.PUBLISHED);
        };
        Map<String, BlogAuthorPo> authorCache = new HashMap<>();
        return rows.stream()
                .map(row -> summary(row, authorCache.computeIfAbsent(row.getAuthorId(), this::requiredAuthor)))
                .toList();
    }

    @Override
    public Optional<Post> findBySlug(String slug, PostQueryScope scope) {
        BlogPostPo row = switch (Objects.requireNonNull(scope, "scope")) {
            case STUDIO -> database.selectBySlug(slug);
            case PUBLISHED -> database.selectBySlugAndStatus(slug, PostStatusPo.PUBLISHED);
        };
        return Optional.ofNullable(row).map(this::rehydrate);
    }

    @Override
    public Optional<Post> lockBySlug(String slug) {
        return Optional.ofNullable(database.lockBySlug(slug)).map(this::rehydrate);
    }

    @Override
    public void add(Post post, PostRevisionEventType eventType) {
        Objects.requireNonNull(post, "post");
        if (eventType != PostRevisionEventType.CREATE && eventType != PostRevisionEventType.IMPORT) {
            throw new IllegalArgumentException("新增文章只能使用 CREATE 或 IMPORT 修订事件");
        }

        BlogPostPo po = mapping.toPostPo(post.snapshot());
        try {
            database.insert(po);
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.SLUG_CONFLICT, "slug 已存在：" + post.slug(), exception);
        }
        insertTags(post.id(), post.tags());
        writeRevision(post, eventType, post.updatedAt());
    }

    @Override
    public void save(PostChange change) {
        Objects.requireNonNull(change, "change");
        if (change.current().archived()) {
            throw new IllegalArgumentException("归档变更必须通过 archive 保存");
        }
        persistChange(change);
    }

    @Override
    public ArchiveReceipt archive(PostChange change) {
        Objects.requireNonNull(change, "change");
        if (change.eventType() != PostRevisionEventType.ARCHIVE || !change.current().archived()) {
            throw new IllegalArgumentException("archive 只接受 ARCHIVE 类型的归档变更");
        }

        Post archived = change.current();
        persistChange(change);
        if (database.deleteCas(archived.id(), archived.slug(), archived.revision()) != 1) {
            throw versionConflict();
        }
        return new ArchiveReceipt(
                archived.slug(),
                archived.status(),
                DATABASE_ARCHIVE_PREFIX + archived.id() + ":" + archived.revision()
        );
    }

    private void persistChange(PostChange change) {
        Post current = change.current();
        BlogPostPo po = mapping.toPostPo(current.snapshot());
        if (database.updateCas(po, change.expectedRevision()) != 1) {
            throw versionConflict();
        }
        replaceTags(current.id(), current.tags());
        writeRevision(current, change.eventType(), current.updatedAt());
    }

    private Post rehydrate(BlogPostPo po) {
        return Post.rehydrate(mapping.toSnapshot(po, database.selectTags(po.getId()), requiredAuthor(po.getAuthorId())));
    }

    private PostSummary summary(BlogPostPo po, BlogAuthorPo author) {
        return mapping.toSummary(po, database.selectTags(po.getId()), author);
    }

    private BlogAuthorPo requiredAuthor(String authorId) {
        BlogAuthorPo author = authors.selectById(authorId);
        if (author == null) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "文章引用的作者不存在：" + authorId);
        }
        return author;
    }

    private void replaceTags(String postId, List<String> tags) {
        database.deleteTags(postId);
        insertTags(postId, tags);
    }

    private void insertTags(String postId, List<String> tags) {
        if (!tags.isEmpty()) {
            database.insertTags(postId, tags);
        }
    }

    private void writeRevision(Post post, PostRevisionEventType eventType, Instant recordedAt) {
        if (database.insertRevisionSnapshot(post.id(), mapping.toPo(eventType), recordedAt) != 1) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "写入文章修订记录失败");
        }
        if (!post.tags().isEmpty()) {
            database.insertRevisionTags(post.id(), post.revision());
        }
    }

    private static BlogException versionConflict() {
        return new BlogException(BlogErrorCode.VERSION_CONFLICT, "文章已被其他操作更新，请刷新后重试");
    }
}
