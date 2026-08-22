package com.speaive.blog.application;

import com.speaive.blog.domain.PostVisibility;

import java.time.Instant;
import java.util.List;

public record PostWriteCommand(
        String slug,
        String title,
        String description,
        Instant publishedAt,
        List<String> tags,
        String cover,
        PostVisibility visibility,
        String body
) {
    public PostWriteCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
        visibility = visibility == null ? PostVisibility.ADMIN_ONLY : visibility;
    }
}
