package com.speaive.blog.domain.post;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.List;

/**
 * 不含正文的文章读取投影，用于列表和相关内容筛选。它不负责写操作，状态修改仍通过 Post 聚合。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param slug 内容 URL 中的路径标识，不是不可重用的数据库 ID
 * @param title 标题
 * @param description 文章或作品集简介
 * @param publishedAt 内容展示的发布时间；不能仅据此判断是否公开
 * @param updatedAt 最近一次修改或状态变化时间
 * @param tags 主题标签列表
 * @param cover 封面地址，未配置时可为空
 * @param author 内容署名身份的不可变表示
 * @param status 当前业务状态，详见该字段的枚举类型
 * @param visibility 可见范围；独立于发布状态
 * @param revision 从 1 开始的修订号，每次合法写操作推进一次
 */
public record PostSummary(
        String id,
        PostSlug slug,
        String title,
        String description,
        Instant publishedAt,
        Instant updatedAt,
        List<String> tags,
        String cover,
        Author author,
        PostStatus status,
        PostVisibility visibility,
        long revision
) {
    public PostSummary {
        PostContent normalized = new PostContent(title, description, publishedAt, tags, cover, "");
        if (id == null || id.isBlank() || id.indexOf(':') >= 0 || slug == null || author == null || status == null
                || visibility == null
                || updatedAt == null || revision < 1) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "文章摘要缺少必要字段");
        }
        id = id.trim();
        title = normalized.title();
        description = normalized.description();
        tags = normalized.tags();
        cover = normalized.cover();
    }

    public PostSummary(
            String id,
            PostSlug slug,
            String title,
            String description,
            Instant publishedAt,
            Instant updatedAt,
            List<String> tags,
            String cover,
            Author author,
            PostStatus status,
            long revision) {
        this(id, slug, title, description, publishedAt, updatedAt, tags, cover,
                author, status, PostVisibility.ADMIN_ONLY, revision);
    }

    public String slugText() {
        return slug.value();
    }

    public String version() {
        return id + ":" + revision;
    }
}
