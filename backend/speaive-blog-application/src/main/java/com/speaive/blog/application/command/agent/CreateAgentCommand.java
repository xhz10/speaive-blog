package com.speaive.blog.application.command.agent;

public record CreateAgentCommand(
        String username,
        String displayName,
        String avatarUrl,
        String systemPrompt,
        String model,
        double temperature,
        boolean canProcessPrivate,
        boolean enabled
) {
}
