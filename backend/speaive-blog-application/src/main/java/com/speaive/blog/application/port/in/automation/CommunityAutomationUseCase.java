package com.speaive.blog.application.port.in.automation;

import com.speaive.blog.application.result.automation.AutomationBatchResult;
import com.speaive.blog.application.result.automation.PostCommunityPolicyResult;

/**
 * 社区自动评论入站契约，供文章设置 HTTP 入口和定时任务调用，不暴露数据库领取实现。
 */
public interface CommunityAutomationUseCase {
    PostCommunityPolicyResult getPostPolicy(String postSlug);

    PostCommunityPolicyResult updatePostPolicy(String postSlug, boolean enabled, long expectedVersion);

    AutomationBatchResult processDueJobs(int limit);
}
