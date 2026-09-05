package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.novel.NovelFragment;
import com.speaive.blog.domain.novel.NovelFragmentChange;
import com.speaive.blog.domain.novel.NovelFragmentRevisionEventType;
import com.speaive.blog.domain.novel.NovelFragmentSummary;

import java.util.List;
import java.util.Optional;

/**
 * 小说片段持久化端口，保存聚合变更及修订历史，并保留公开读取过滤和版本竞争语义。
 */
public interface NovelFragmentRepository {
    List<NovelFragmentSummary> findAll(NovelFragmentQueryScope scope);

    Optional<NovelFragment> findBySlug(String slug, NovelFragmentQueryScope scope);

    Optional<NovelFragment> lockBySlug(String slug);

    void add(NovelFragment fragment, NovelFragmentRevisionEventType eventType);

    void save(NovelFragmentChange change);
}
