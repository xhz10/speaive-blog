package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.CommunityCommentJobRepository;
import com.speaive.blog.domain.automation.CommunityCommentJob;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCommunityCommentJobDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PostgresCommunityCommentJobRepository implements CommunityCommentJobRepository {
    private final BlogCommunityCommentJobDatabaseMapper database;
    private final BlogAiPersistenceMapStructMapper mapping;

    public PostgresCommunityCommentJobRepository(
            BlogCommunityCommentJobDatabaseMapper database,
            BlogAiPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public boolean addIfAbsent(CommunityCommentJob job) {
        try {
            return database.insert(mapping.toCommunityCommentJobPo(job)) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public Optional<CommunityCommentJob> claimNext(Instant now, Instant staleBefore) {
        return Optional.ofNullable(database.claimNext(now, staleBefore)).map(mapping::toCommunityCommentJob);
    }

    @Override
    public void save(CommunityCommentJob job) {
        if (database.finish(mapping.toCommunityCommentJobPo(job)) != 1) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT,
                    "自动评论任务已被其他工作线程更新");
        }
    }
}
