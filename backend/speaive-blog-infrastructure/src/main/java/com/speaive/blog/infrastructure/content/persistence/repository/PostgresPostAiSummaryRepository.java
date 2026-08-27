package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.PostAiSummaryRepository;
import com.speaive.blog.domain.post.PostAiSummary;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogPostAiSummaryDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PostgresPostAiSummaryRepository implements PostAiSummaryRepository {
    private final BlogPostAiSummaryDatabaseMapper database;
    private final BlogAiPersistenceMapStructMapper mapping;

    public PostgresPostAiSummaryRepository(
            BlogPostAiSummaryDatabaseMapper database,
            BlogAiPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public Optional<PostAiSummary> findByPostId(String postId) {
        return Optional.ofNullable(database.selectByPostId(postId)).map(mapping::toPostAiSummary);
    }

    @Override
    public List<PostAiSummary> findByPostIds(Set<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return database.selectByPostIds(Set.copyOf(postIds)).stream()
                .map(mapping::toPostAiSummary)
                .toList();
    }

    @Override
    public void save(PostAiSummary summary) {
        int affected = database.upsert(mapping.toPostAiSummaryPo(summary));
        if (affected < 0 || affected > 1) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "保存 AI 摘要失败");
        }
    }
}
