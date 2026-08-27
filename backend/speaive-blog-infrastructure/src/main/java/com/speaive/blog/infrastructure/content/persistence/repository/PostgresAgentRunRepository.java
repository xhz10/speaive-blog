package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.AgentRunRepository;
import com.speaive.blog.domain.agent.AgentRun;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAgentRunDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Objects;

public final class PostgresAgentRunRepository implements AgentRunRepository {
    private final BlogAgentRunDatabaseMapper database;
    private final BlogAiPersistenceMapStructMapper mapping;

    public PostgresAgentRunRepository(
            BlogAgentRunDatabaseMapper database,
            BlogAiPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public void add(AgentRun run) {
        try {
            if (database.insert(mapping.toRunPo(run)) != 1) {
                throw storage("创建 Agent 执行记录失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.GENERATION_CONFLICT,
                    "该 Agent 已经为当前文章版本生成过评论", exception);
        }
    }

    @Override
    public void save(AgentRun run) {
        if (database.finish(mapping.toRunPo(run)) != 1) {
            throw storage("更新 Agent 执行记录失败");
        }
    }

    @Override
    public void failStaleRunning(
            String postId,
            long postRevision,
            String agentId,
            String targetCommentId,
            Instant startedBefore,
            Instant completedAt) {
        database.failStaleRunning(postId, postRevision, agentId, targetCommentId, startedBefore, completedAt);
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
