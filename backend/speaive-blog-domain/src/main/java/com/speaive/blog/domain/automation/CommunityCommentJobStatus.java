package com.speaive.blog.domain.automation;

/** 社区 Agent 自动评论队列状态；与单次模型调用的 AgentRunStatus 分开记录。 */
public enum CommunityCommentJobStatus {
    /** 等待执行：尚未领取，或失败后等待 availableAt 到达再重试。 */
    PENDING,
    /** 已领取执行中：领取过程递增尝试次数，并记录 claimedAt。 */
    RUNNING,
    /** 任务完成：生成结果已进入待审核，不表示已发布。 */
    SUCCEEDED,
    /** 无需继续：目标不存在、版本变化、请求不符合条件或重复生成等原因使本次任务失效。 */
    SKIPPED,
    /** 最终失败：达到重试上限，保存最后一次错误说明。 */
    FAILED
}
