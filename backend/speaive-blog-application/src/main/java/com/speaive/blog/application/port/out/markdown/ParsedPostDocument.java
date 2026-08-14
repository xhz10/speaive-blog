package com.speaive.blog.application.port.out.markdown;

import java.time.Instant;
import java.util.List;

public record ParsedPostDocument(
        String slug,
        String title,
        String description,
        Instant publishedAt,
        List<String> tags,
        String cover,
        String body
) {
    public ParsedPostDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
