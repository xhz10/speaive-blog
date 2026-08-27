package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAgentDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAgentPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresAgentRepository implements AgentRepository {
    private final BlogAgentDatabaseMapper database;
    private final BlogAuthorDatabaseMapper authors;
    private final BlogAiPersistenceMapStructMapper mapping;

    public PostgresAgentRepository(
            BlogAgentDatabaseMapper database,
            BlogAuthorDatabaseMapper authors,
            BlogAiPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public List<AgentProfile> findAll() {
        return database.selectAllAgents().stream().map(this::rehydrate).toList();
    }

    @Override
    public Optional<AgentProfile> findById(String id) {
        return Optional.ofNullable(database.selectAgentById(id)).map(this::rehydrate);
    }

    @Override
    public List<AgentProfile> findByOwnerAccountId(String ownerAccountId) {
        return database.selectByOwnerAccountId(ownerAccountId).stream().map(this::rehydrate).toList();
    }

    @Override
    public void add(AgentProfile agent) {
        BlogAuthorPo author = mapping.toAuthorPo(agent.identity());
        try {
            if (authors.insertAgentAuthor(author, agent.createdAt()) != 1 || database.insert(mapping.toAgentPo(agent)) != 1) {
                throw storage("创建 Agent 失败");
            }
            replaceAutoTags(agent);
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "Agent 用户名已经存在", exception);
        }
    }

    @Override
    public void save(AgentProfile agent, long expectedVersion) {
        if (authors.updateAgentAuthor(mapping.toAuthorPo(agent.identity()), agent.updatedAt()) != 1) {
            throw storage("更新 Agent 身份失败");
        }
        if (database.updateCas(mapping.toAgentPo(agent), expectedVersion) != 1) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "Agent 已在其他位置更新，请刷新后重试");
        }
        replaceAutoTags(agent);
    }

    private AgentProfile rehydrate(BlogAgentPo agent) {
        BlogAuthorPo author = authors.selectById(agent.getId());
        if (author == null) {
            throw storage("Agent 关联的作者身份不存在");
        }
        return AgentProfile.rehydrate(
                mapping.toAuthor(author), agent.getOwnerAccountId(), agent.getSystemPrompt(), agent.getModel(),
                agent.getTemperature(), agent.isCanProcessPrivate(), agent.isEnabledRequested(),
                mapping.toDomain(agent.getReviewStatus()), agent.getReviewNote(), agent.getReviewedAt(),
                agent.isAutoCommentEnabled(), agent.isAutoCommentAllPosts(), database.selectAutoTags(agent.getId()),
                agent.getPromptVersion(), agent.getCreatedAt(), agent.getUpdatedAt());
    }

    private void replaceAutoTags(AgentProfile agent) {
        database.deleteAutoTags(agent.id());
        for (int index = 0; index < agent.autoCommentTags().size(); index++) {
            if (database.insertAutoTag(agent.id(), agent.autoCommentTags().get(index), index) != 1) {
                throw storage("保存 Agent 自动评论标签失败");
            }
        }
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
