package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.creative.ContentRevision;
import com.speaive.blog.domain.creative.CreativeContentType;

import java.util.List;
import java.util.Optional;

/**
 * 文章与小说修订历史的只读端口，读取历史快照而不直接修改当前内容。
 */
public interface ContentRevisionRepository {
    List<ContentRevision> findAll(CreativeContentType contentType, String contentSlug);

    Optional<ContentRevision> find(CreativeContentType contentType, String contentSlug, long revision);
}
