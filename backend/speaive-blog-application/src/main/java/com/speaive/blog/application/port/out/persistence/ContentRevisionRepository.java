package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.creative.ContentRevision;
import com.speaive.blog.domain.creative.CreativeContentType;

import java.util.List;
import java.util.Optional;

public interface ContentRevisionRepository {
    List<ContentRevision> findAll(CreativeContentType contentType, String contentSlug);

    Optional<ContentRevision> find(CreativeContentType contentType, String contentSlug, long revision);
}
