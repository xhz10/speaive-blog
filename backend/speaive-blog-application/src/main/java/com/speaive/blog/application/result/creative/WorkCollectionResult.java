package com.speaive.blog.application.result.creative;

import java.time.Instant;
import java.util.List;

public record WorkCollectionResult(
        String slug,
        String title,
        String description,
        String cover,
        String visibility,
        long revision,
        Instant updatedAt,
        List<WorkItemResult> items
) {
    public WorkCollectionResult {
        items = List.copyOf(items);
    }

    public record WorkItemResult(
            String contentType,
            String contentSlug,
            int position,
            String title,
            String summary,
            String href,
            Instant publishedAt
    ) {
    }
}
