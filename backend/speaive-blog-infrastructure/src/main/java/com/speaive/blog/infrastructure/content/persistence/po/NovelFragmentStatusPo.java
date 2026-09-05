package com.speaive.blog.infrastructure.content.persistence.po;

/** NovelFragmentStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum NovelFragmentStatusPo {
    /** 草稿：小说片段尚未发布，访客不可读取。 */
    DRAFT,
    /** 已发布：还需同时满足 PUBLIC 可见性，访客才能读取小说片段。 */
    PUBLISHED
}
