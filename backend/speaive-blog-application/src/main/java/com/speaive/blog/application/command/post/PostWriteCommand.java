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
        String body
) {
    public PostWriteCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
