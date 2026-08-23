package com.speaive.blog.application.command.post;

import java.time.Instant;
import java.util.List;

public record PostWriteCommand(
        String slug,
        String title,
        String description,
        Instant publishedAt,
        List<String> tags,
        String cover,
        String visibility,
        String body
) {
    public PostWriteCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
        visibility = visibility == null || visibility.isBlank() ? "ADMIN_ONLY" : visibility;
    }

    public PostWriteCommand(
            String slug,
            String title,
            String description,
            Instant publishedAt,
            List<String> tags,
            String cover,
            String body) {
        this(slug, title, description, publishedAt, tags, cover, "ADMIN_ONLY", body);
    }
}
