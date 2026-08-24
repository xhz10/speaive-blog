package com.speaive.blog.application.command.agent;

public record UpdateAgentCommand(
        String displayName,
        String avatarUrl,
        String systemPrompt,
        String model,
        double temperature,
        boolean canProcessPrivate,
        boolean enabled,
        long version
) {
}
