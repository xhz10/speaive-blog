package com.speaive.blog.domain.creative;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record WorkCollection(
        String id,
        String slug,
        String title,
        String description,
        String cover,
        CreativeVisibility visibility,
        List<WorkItem> items,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkCollection {
        id = required(id, 36, "作品集 ID 不能为空");
        slug = required(slug, 100, "作品集 slug 不能为空");
        title = required(title, 200, "作品集标题不能为空");
        description = optional(description, 800, "作品集简介不能超过 800 个字符");
        cover = optional(cover, 2048, "作品集封面地址过长");
        visibility = Objects.requireNonNull(visibility, "visibility");
        items = normalizeItems(items);
        if (revision < 1) throw invalid("作品集 revision 必须大于 0");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw invalid("作品集更新时间不能早于创建时间");
    }

    public static WorkCollection create(String id, String slug, String title, String description, String cover,
                                        CreativeVisibility visibility, List<WorkItem> items, Instant now) {
        return new WorkCollection(id, slug, title, description, cover, visibility, items, 1, now, now);
    }

    public WorkCollection edit(String title, String description, String cover, CreativeVisibility visibility,
                               List<WorkItem> items, long expectedRevision, Instant now) {
        if (revision != expectedRevision) throw invalid("作品集已在其他页面更新，请刷新后重试");
        if (now.isBefore(updatedAt)) throw invalid("作品集更新时间不能倒退");
        return new WorkCollection(id, slug, title, description, cover, visibility, items,
                revision + 1, createdAt, now);
    }

    private static List<WorkItem> normalizeItems(List<WorkItem> source) {
        List<WorkItem> values = source == null ? List.of() : source;
        if (values.size() > 200) throw invalid("一个作品集最多包含 200 条内容");
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < values.size(); index++) {
            WorkItem item = values.get(index);
            if (item.position() != index) throw invalid("作品集条目顺序必须连续");
            if (!unique.add(item.contentType() + ":" + item.contentSlug())) throw invalid("作品集不能重复添加同一内容");
        }
        return List.copyOf(values);
    }

    private static String required(String value, int max, String message) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.codePointCount(0, result.length()) > max) throw invalid(message);
        return result;
    }

    private static String optional(String value, int max, String message) {
        String result = value == null ? "" : value.trim();
        if (result.codePointCount(0, result.length()) > max) throw invalid(message);
        return result;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_STATE, message);
    }
}
