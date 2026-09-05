package com.speaive.blog.infrastructure.content.persistence.po;

/** AgentRunStatus 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum AgentRunStatusPo {
    /** 生成中：已经登记本次文章版本、Agent 和回复目标，等待模型返回。 */
    RUNNING,
    /** 生成成功：已保存待审核评论，同时记录模型与可用的 token 用量。 */
    SUCCEEDED,
    /** 生成失败或超时占位已清理；保存错误摘要供排查。 */
    FAILED
}
