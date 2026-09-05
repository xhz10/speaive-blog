package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.automation.CommunityPostPolicy;

import java.util.Optional;

/**
 * 文章自动评论开关的持久化端口，提供锁定与按预期版本保存，避免并发修改相互覆盖。
 */
public interface CommunityPostPolicyRepository {
    Optional<CommunityPostPolicy> findByPostId(String postId);

    Optional<CommunityPostPolicy> lockByPostId(String postId);

    void add(CommunityPostPolicy policy);

    void save(CommunityPostPolicy policy, long expectedVersion);
}
