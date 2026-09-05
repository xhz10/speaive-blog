package com.speaive.blog.infrastructure.content.persistence.po;

/** AuthorStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum AuthorStatusPo {
    /** 启用身份；是否可以写作还需结合 AuthorType 判断。 */
    ACTIVE,
    /** 停用身份；不再允许以该身份创建新内容。 */
    DISABLED
}
