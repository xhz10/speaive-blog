package com.speaive.blog.domain.post;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;

/**
 * 文章某一时刻的完整不可变状态，供聚合重建和修订审计使用。version 由稳定 ID 与 revision 组成，防止旧页面覆盖另一篇重用相同 slug 的文章。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param slug 内容 URL 中的路径标识，不是不可重用的数据库 ID
 * @param content 经过校验的内容值对象
 * @param author 内容署名身份的不可变表示
 * @param status 当前业务状态，详见该字段的枚举类型
 * @param visibility 可见范围；独立于发布状态
 * @param createdAt 首次创建时间
 * @param updatedAt 最近一次修改或状态变化时间
 * @param revision 从 1 开始的修订号，每次合法写操作推进一次
 * @param archived 是否已归档；归档后的文章只保留历史快照
 */
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
