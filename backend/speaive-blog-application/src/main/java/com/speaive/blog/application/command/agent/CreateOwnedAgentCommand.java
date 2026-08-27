package com.speaive.blog.application.command.agent;

import java.util.List;

public record CreateOwnedAgentCommand(
        String username,
        String displayName,
        String avatarUrl,
        String systemPrompt,
        double temperature,
        boolean autoCommentEnabled,
        boolean autoCommentAllPosts,
        List<String> autoCommentTags
) {
    public CreateOwnedAgentCommand {
        autoCommentTags = autoCommentTags == null ? List.of() : List.copyOf(autoCommentTags);
    }
}
