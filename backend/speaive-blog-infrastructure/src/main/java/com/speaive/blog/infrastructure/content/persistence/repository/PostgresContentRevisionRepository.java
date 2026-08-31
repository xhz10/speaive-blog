package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.port.out.persistence.ContentRevisionRepository;
import com.speaive.blog.domain.creative.ContentRevision;
import com.speaive.blog.domain.creative.CreativeContentType;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCreativeDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.CreativePersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogContentRevisionPo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresContentRevisionRepository implements ContentRevisionRepository {
    private final BlogCreativeDatabaseMapper database;
    private final CreativePersistenceMapStructMapper mapping;

    public PostgresContentRevisionRepository(
            BlogCreativeDatabaseMapper database,
            CreativePersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public List<ContentRevision> findAll(CreativeContentType contentType, String contentSlug) {
        List<BlogContentRevisionPo> rows = contentType == CreativeContentType.POST
                ? database.selectPostRevisions(contentSlug)
                : database.selectNovelRevisions(contentSlug);
        return rows.stream().map(row -> mapping.toContentRevision(
                row, contentType, contentType == CreativeContentType.POST
                        ? database.selectPostRevisionTags(row.getContentId(), row.getRevision())
                        : List.of())).toList();
    }

    @Override
    public Optional<ContentRevision> find(
            CreativeContentType contentType, String contentSlug, long revision) {
        return findAll(contentType, contentSlug).stream()
                .filter(value -> value.revision() == revision)
                .findFirst();
    }
}
