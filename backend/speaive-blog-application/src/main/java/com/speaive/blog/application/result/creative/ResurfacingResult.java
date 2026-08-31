package com.speaive.blog.application.result.creative;

import java.util.List;

public record ResurfacingResult(List<ResurfacingItem> items) {
    public ResurfacingResult {
        items = List.copyOf(items);
    }

    public record ResurfacingItem(
            String kind,
            String title,
            String description,
            String href,
            List<String> tags,
            List<String> relatedSlugs
    ) {
        public ResurfacingItem {
            tags = List.copyOf(tags);
            relatedSlugs = List.copyOf(relatedSlugs);
        }
    }
}
