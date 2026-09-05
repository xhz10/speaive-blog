package com.speaive.blog.infrastructure.content.persistence.po;

/** PostVisibility 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum PostVisibilityPo {
    /** 公开范围：文章满足对应发布规则后可供访客读取。 */
    PUBLIC,
    /** 仅管理员：文章及受保护的关联内容只供站长查看，会员身份不等于管理员。 */
    ADMIN_ONLY
}
