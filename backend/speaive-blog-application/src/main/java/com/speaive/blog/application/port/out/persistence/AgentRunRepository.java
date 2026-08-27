package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.agent.AgentRun;

import java.time.Instant;

public interface AgentRunRepository {
    void add(AgentRun run);

    void save(AgentRun run);

    void failStaleRunning(
            String postId,
            long postRevision,
            String agentId,
            String targetCommentId,
            Instant startedBefore,
            Instant completedAt);
}
