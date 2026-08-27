package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.automation.CommunityPostPolicy;

import java.util.Optional;

public interface CommunityPostPolicyRepository {
    Optional<CommunityPostPolicy> findByPostId(String postId);

    Optional<CommunityPostPolicy> lockByPostId(String postId);

    void add(CommunityPostPolicy policy);

    void save(CommunityPostPolicy policy, long expectedVersion);
}
