package com.speaive.blog.application.port.in.automation;

import com.speaive.blog.application.result.automation.AutomationBatchResult;
import com.speaive.blog.application.result.automation.PostCommunityPolicyResult;

public interface CommunityAutomationUseCase {
    PostCommunityPolicyResult getPostPolicy(String postSlug);

    PostCommunityPolicyResult updatePostPolicy(String postSlug, boolean enabled, long expectedVersion);

    AutomationBatchResult processDueJobs(int limit);
}
