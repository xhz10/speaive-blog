package com.speaive.blog.domain.post;

/** 文章的发布状态；与可见范围是两个独立维度，已发布不自动等于公开。 */
public enum PostStatus {
    /** 草稿：文章尚未发布，访客不可读取。 */
    DRAFT,
    /** 已发布：还需同时满足 PUBLIC 可见性，访客才能读取文章。 */
    PUBLISHED
}
