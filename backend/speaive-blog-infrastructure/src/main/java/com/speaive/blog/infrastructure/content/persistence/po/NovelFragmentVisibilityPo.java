package com.speaive.blog.infrastructure.content.persistence.po;

/** NovelFragmentVisibility 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum NovelFragmentVisibilityPo {
    /** 公开范围：小说片段满足对应发布规则后可供访客读取。 */
    PUBLIC,
    /** 仅管理员：小说片段及受保护的关联内容只供站长查看，会员身份不等于管理员。 */
    ADMIN_ONLY
}
