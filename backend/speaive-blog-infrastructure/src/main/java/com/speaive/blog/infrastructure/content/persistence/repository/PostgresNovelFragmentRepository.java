package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.NovelFragmentQueryScope;
import com.speaive.blog.application.port.out.persistence.NovelFragmentRepository;
import com.speaive.blog.domain.novel.NovelFragment;
import com.speaive.blog.domain.novel.NovelFragmentChange;
import com.speaive.blog.domain.novel.NovelFragmentRevisionEventType;
import com.speaive.blog.domain.novel.NovelFragmentSummary;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogNovelFragmentDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.NovelFragmentPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogNovelFragmentPo;
import com.speaive.blog.infrastructure.content.persistence.po.NovelFragmentStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.NovelFragmentVisibilityPo;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PostgresNovelFragmentRepository implements NovelFragmentRepository {
    private final BlogNovelFragmentDatabaseMapper database;
    private final BlogAuthorDatabaseMapper authors;
    private final NovelFragmentPersistenceMapStructMapper mapping;

    public PostgresNovelFragmentRepository(
            BlogNovelFragmentDatabaseMapper database,
            BlogAuthorDatabaseMapper authors,
            NovelFragmentPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public List<NovelFragmentSummary> findAll(NovelFragmentQueryScope scope) {
        List<BlogNovelFragmentPo> rows = switch (Objects.requireNonNull(scope, "scope")) {
            case STUDIO -> database.selectAllFragments();
            case PUBLISHED -> database.selectPublishedFragments(
                    NovelFragmentStatusPo.PUBLISHED, NovelFragmentVisibilityPo.PUBLIC);
        };
        Map<String, BlogAuthorPo> authorCache = new HashMap<>();
        return rows.stream()
                .map(row -> mapping.toSummary(
                        row, authorCache.computeIfAbsent(row.getAuthorId(), this::requiredAuthor)))
                .toList();
    }

    @Override
    public Optional<NovelFragment> findBySlug(String slug, NovelFragmentQueryScope scope) {
        BlogNovelFragmentPo row = switch (Objects.requireNonNull(scope, "scope")) {
            case STUDIO -> database.selectBySlug(slug);
            case PUBLISHED -> database.selectBySlugAndStatusAndVisibility(
                    slug, NovelFragmentStatusPo.PUBLISHED, NovelFragmentVisibilityPo.PUBLIC);
        };
        return Optional.ofNullable(row).map(this::rehydrate);
    }

    @Override
    public Optional<NovelFragment> lockBySlug(String slug) {
        return Optional.ofNullable(database.lockBySlug(slug)).map(this::rehydrate);
    }

    @Override
    public void add(NovelFragment fragment, NovelFragmentRevisionEventType eventType) {
        Objects.requireNonNull(fragment, "fragment");
        if (eventType != NovelFragmentRevisionEventType.CREATE) {
            throw new IllegalArgumentException("新增小说片段只能使用 CREATE 修订事件");
        }
        try {
            database.insert(mapping.toPo(fragment.snapshot()));
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.SLUG_CONFLICT,
                    "小说片段 slug 已存在：" + fragment.slug(), exception);
        }
        writeRevision(fragment, eventType);
    }

    @Override
    public void save(NovelFragmentChange change) {
        Objects.requireNonNull(change, "change");
        NovelFragment current = change.current();
        if (database.updateCas(mapping.toPo(current.snapshot()), change.expectedRevision()) != 1) {
            throw versionConflict();
        }
        writeRevision(current, change.eventType());
    }

    private NovelFragment rehydrate(BlogNovelFragmentPo row) {
        return NovelFragment.rehydrate(mapping.toSnapshot(row, requiredAuthor(row.getAuthorId())));
    }

    private BlogAuthorPo requiredAuthor(String authorId) {
        BlogAuthorPo author = authors.selectById(authorId);
        if (author == null) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR,
                    "小说片段引用的作者不存在：" + authorId);
        }
        return author;
    }

    private void writeRevision(NovelFragment fragment, NovelFragmentRevisionEventType eventType) {
        if (database.insertRevisionSnapshot(
                fragment.id(), mapping.toPo(eventType), fragment.updatedAt()) != 1) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "写入小说片段修订记录失败");
        }
    }

    private static BlogException versionConflict() {
        return new BlogException(BlogErrorCode.VERSION_CONFLICT,
                "小说片段已被其他操作更新，请刷新后重试");
    }
}
