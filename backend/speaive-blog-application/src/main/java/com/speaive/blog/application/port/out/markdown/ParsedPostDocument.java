package com.speaive.blog.application.port.out.markdown;

import com.speaive.blog.domain.post.PostVisibility;

import java.time.Instant;
import java.util.List;

/**
 * Markdown 适配器产出的文章草稿数据，后续仍需通过 PostContent 和 Post 校验才能保存。
 */
public record ParsedPostDocument(
        String slug,
        String title,
        String description,
        Instant publishedAt,
        List<String> tags,
        String cover,
        PostVisibility visibility,
        String body
) {
    public ParsedPostDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
        visibility = visibility == null ? PostVisibility.ADMIN_ONLY : visibility;
    }

    public ParsedPostDocument(
            String slug,
            String title,
            String description,
            Instant publishedAt,
            List<String> tags,
            String cover,
            String body) {
        this(slug, title, description, publishedAt, tags, cover, PostVisibility.ADMIN_ONLY, body);
    }
}
