package com.speaive.blog.application.result.automation;

public record PostCommunityPolicyResult(
        String postSlug,
        boolean enabled,
        long version,
        int queuedAgents
) {
}
