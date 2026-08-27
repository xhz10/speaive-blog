package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.CommunityPostPolicyRepository;
import com.speaive.blog.domain.automation.CommunityPostPolicy;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCommunityPostPolicyDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;

import java.util.Objects;
import java.util.Optional;

public final class PostgresCommunityPostPolicyRepository implements CommunityPostPolicyRepository {
    private final BlogCommunityPostPolicyDatabaseMapper database;
    private final BlogAiPersistenceMapStructMapper mapping;

    public PostgresCommunityPostPolicyRepository(
            BlogCommunityPostPolicyDatabaseMapper database,
            BlogAiPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public Optional<CommunityPostPolicy> findByPostId(String postId) {
        return Optional.ofNullable(database.selectById(postId)).map(mapping::toCommunityPostPolicy);
    }

    @Override
    public Optional<CommunityPostPolicy> lockByPostId(String postId) {
        return Optional.ofNullable(database.lockByPostId(postId)).map(mapping::toCommunityPostPolicy);
    }

    @Override
    public void add(CommunityPostPolicy policy) {
        if (database.insert(mapping.toCommunityPostPolicyPo(policy)) != 1) {
            throw storage("保存文章社区 Agent 设置失败");
        }
    }

    @Override
    public void save(CommunityPostPolicy policy, long expectedVersion) {
        if (database.updateCas(mapping.toCommunityPostPolicyPo(policy), expectedVersion) != 1) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT,
                    "社区 Agent 设置已更新，请刷新后重试");
        }
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
