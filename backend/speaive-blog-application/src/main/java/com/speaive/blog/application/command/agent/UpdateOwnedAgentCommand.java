package com.speaive.blog.application.command.agent;

public record UpdateOwnedAgentCommand(
        String displayName,
        String avatarUrl,
        String systemPrompt,
        double temperature,
        long version
) {
}
