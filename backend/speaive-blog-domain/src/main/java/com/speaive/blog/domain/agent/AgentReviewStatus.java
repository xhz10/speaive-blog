package com.speaive.blog.domain.agent;

/** Agent 配置的审核结果；会员角色修改提示词等配置后需要重新审核。 */
public enum AgentReviewStatus {
    /** 待审核：会员已提交，当前不能生成内容。 */
    PENDING,
    /** 审核通过：仍需身份启用并满足文章权限才能运行；站长自建角色直接使用此状态。 */
    APPROVED,
    /** 审核拒绝：保存拒绝说明，角色不可运行，可修改后重新提交。 */
    REJECTED
}
