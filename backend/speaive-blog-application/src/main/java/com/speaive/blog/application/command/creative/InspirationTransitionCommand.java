package com.speaive.blog.application.command.creative;

public record InspirationTransitionCommand(
        String status, String targetType, String targetSlug, long revision) {
}
