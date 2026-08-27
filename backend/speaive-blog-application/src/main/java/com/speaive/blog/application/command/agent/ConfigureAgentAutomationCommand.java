package com.speaive.blog.application.command.agent;

import java.util.List;

public record ConfigureAgentAutomationCommand(
        boolean enabled,
        boolean autoCommentEnabled,
        boolean autoCommentAllPosts,
        List<String> autoCommentTags,
        long version
) {
    public ConfigureAgentAutomationCommand {
        autoCommentTags = autoCommentTags == null ? List.of() : List.copyOf(autoCommentTags);
    }
}
