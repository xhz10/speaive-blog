package com.speaive.blog.infrastructure.content.persistence.po;

/** AgentReviewStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum AgentReviewStatusPo {
    /** 待审核：会员已提交，当前不能生成内容。 */
    PENDING,
    /** 审核通过：仍需身份启用并满足文章权限才能运行；站长自建角色直接使用此状态。 */
    APPROVED,
    /** 审核拒绝：保存拒绝说明，角色不可运行，可修改后重新提交。 */
    REJECTED
}
