package com.speaive.blog.domain.post;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;

public record PostSnapshot(
        String id,
        PostSlug slug,
        PostContent content,
        Author author,
        PostStatus status,
        PostVisibility visibility,
        Instant createdAt,
        Instant updatedAt,
        long revision,
        boolean archived
) {
    public PostSnapshot {
        if (id == null || id.isBlank() || id.indexOf(':') >= 0) {
            throw invalid("文章 ID 不能为空且不能包含冒号");
        }
        id = id.trim();
        if (slug == null || content == null || author == null || status == null || visibility == null
                || createdAt == null || updatedAt == null) {
            throw invalid("文章快照缺少必要字段");
        }
        if (revision < 1) {
            throw invalid("文章 revision 必须大于 0");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw invalid("文章更新时间不能早于创建时间");
        }
    }

    public PostSnapshot(
            String id,
            PostSlug slug,
            PostContent content,
            Author author,
            PostStatus status,
            Instant createdAt,
            Instant updatedAt,
            long revision,
            boolean archived) {
        this(id, slug, content, author, status, PostVisibility.ADMIN_ONLY,
                createdAt, updatedAt, revision, archived);
    }

    public String version() {
        return id + ":" + revision;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_STATE, message);
    }
}
