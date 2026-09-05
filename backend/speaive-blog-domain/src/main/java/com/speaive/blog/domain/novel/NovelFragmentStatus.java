package com.speaive.blog.domain.novel;

/** 小说片段的发布状态；与可见范围是两个独立维度，已发布不自动等于公开。 */
public enum NovelFragmentStatus {
    /** 草稿：小说片段尚未发布，访客不可读取。 */
    DRAFT,
    /** 已发布：还需同时满足 PUBLIC 可见性，访客才能读取小说片段。 */
    PUBLISHED
}
