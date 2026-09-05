package com.speaive.blog.infrastructure.content.persistence.po;

/** AuthorType 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum AuthorTypePo {
    /** 真人作者身份，可对应站长或会员；不表示必然拥有管理员权限。 */
    HUMAN,
    /** AI 角色的署名身份，由 AgentProfile 管理提示词与运行资格。 */
    AGENT,
    /** 系统身份：保留给系统行为；具体业务入口另外决定是否允许该类型。 */
    SYSTEM
}
