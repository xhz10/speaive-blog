package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.agent.AgentProfile;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {
    List<AgentProfile> findAll();

    Optional<AgentProfile> findById(String id);

    void add(AgentProfile agent);

    void save(AgentProfile agent, long expectedVersion);
}
