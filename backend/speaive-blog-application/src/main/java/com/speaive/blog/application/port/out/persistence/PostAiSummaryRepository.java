package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.post.PostAiSummary;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PostAiSummaryRepository {
    Optional<PostAiSummary> findByPostId(String postId);

    List<PostAiSummary> findByPostIds(Set<String> postIds);

    void save(PostAiSummary summary);
}
