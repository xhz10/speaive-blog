package com.speaive.blog.infrastructure.content.persistence.po;

/** CommentStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum CommentStatusPo {
    /** 待审核：站长可见，访客不可见；允许其他角色继续生成待审核回复。 */
    PENDING,
    /** 已公开：回复还要求父评论已公开，文章本身也必须允许公开读取。 */
    PUBLISHED,
    /** 已隐藏：不能继续回复或直接重新发布；隐藏用例会连同后续回复一起处理。 */
    HIDDEN
}
