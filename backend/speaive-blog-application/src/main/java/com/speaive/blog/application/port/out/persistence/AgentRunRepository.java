package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.agent.AgentRun;

import java.time.Instant;

/**
 * 模型生成审计端口，负责登记生成占位、保存结果和清理超时记录；实现需通过唯一约束防止同一生成任务重复占位。
 */
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
