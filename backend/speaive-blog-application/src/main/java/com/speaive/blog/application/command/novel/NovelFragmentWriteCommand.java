package com.speaive.blog.application.command.novel;

public record NovelFragmentWriteCommand(
        String slug,
        String title,
        String excerpt,
        String visibility,
        String body
) {
    public NovelFragmentWriteCommand {
        visibility = visibility == null || visibility.isBlank() ? "ADMIN_ONLY" : visibility.trim();
    }
}
