package com.speaive.blog.domain.agent;

/** 一次 AI 评论或回复调用的审计状态；成功生成不代表评论已经公开。 */
public enum AgentRunStatus {
    /** 生成中：已经登记本次文章版本、Agent 和回复目标，等待模型返回。 */
    RUNNING,
    /** 生成成功：已保存待审核评论，同时记录模型与可用的 token 用量。 */
    SUCCEEDED,
    /** 生成失败或超时占位已清理；保存错误摘要供排查。 */
    FAILED
}
