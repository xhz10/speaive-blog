package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.agent.AgentProfile;

import java.util.List;
import java.util.Optional;

/**
 * Agent 配置持久化端口，支持按会员归属查询和携带预期版本保存。
 */
public interface AgentRepository {
    List<AgentProfile> findAll();

    Optional<AgentProfile> findById(String id);

    List<AgentProfile> findByOwnerAccountId(String ownerAccountId);

    void add(AgentProfile agent);

    void save(AgentProfile agent, long expectedVersion);
}
