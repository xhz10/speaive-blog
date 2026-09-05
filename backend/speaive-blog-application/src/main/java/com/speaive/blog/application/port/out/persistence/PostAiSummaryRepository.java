package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.post.PostAiSummary;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 文章记忆摘要持久化端口，支持按文章批量读取；摘要是否对应当前修订由领域对象和用例判断。
 */
public interface PostAiSummaryRepository {
    Optional<PostAiSummary> findByPostId(String postId);

    List<PostAiSummary> findByPostIds(Set<String> postIds);

    void save(PostAiSummary summary);
}
