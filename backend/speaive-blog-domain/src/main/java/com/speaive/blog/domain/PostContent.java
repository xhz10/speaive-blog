package com.speaive.blog.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record PostContent(
        String title,
        String description,
        Instant publishedAt,
        List<String> tags,
        String cover,
        String body
) {
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_TAG_COUNT = 20;
    public static final int MAX_TAG_LENGTH = 40;

    private static final Pattern COVER_PATTERN = Pattern.compile(
            "^/media/[A-Za-z0-9/_-]+\\.(?:avif|gif|jpe?g|png|webp)$",
            Pattern.CASE_INSENSITIVE
    );

    public PostContent {
        title = requireText(title, "标题不能为空", MAX_TITLE_LENGTH);
        description = optionalText(description, "摘要不能超过 500 个字符", MAX_DESCRIPTION_LENGTH);
        if (publishedAt == null) {
            throw invalid("发布时间不能为空");
        }
        tags = normalizeTags(tags);
        cover = normalizeCover(cover);
        if (body == null) {
            throw invalid("正文不能为空");
        }
        body = body.trim();
        if (body.indexOf('\0') >= 0) {
            throw invalid("正文包含非法字符");
        }
    }

    private static List<String> normalizeTags(List<String> values) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > MAX_TAG_COUNT) {
            throw invalid("标签不能超过 20 个");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : source) {
            unique.add(requireText(value, "标签不能为空", MAX_TAG_LENGTH));
        }
        return List.copyOf(unique);
    }

    private static String normalizeCover(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cover = value.trim();
        if (!COVER_PATTERN.matcher(cover).matches() || cover.contains("..") || cover.contains("//")) {
            throw invalid("封面必须使用 /media/ 下的已上传图片");
        }
        return cover;
    }

    private static String requireText(String value, String emptyMessage, int maxLength) {
        String result = value == null ? "" : value.trim();
        int length = result.codePointCount(0, result.length());
        if (length == 0) {
            throw invalid(emptyMessage);
        }
        if (length > maxLength) {
            throw invalid(emptyMessage.replace("不能为空", "不能超过 " + maxLength + " 个字符"));
        }
        return result;
    }

    private static String optionalText(String value, String tooLongMessage, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.codePointCount(0, result.length()) > maxLength) {
            throw invalid(tooLongMessage);
        }
        return result;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_CONTENT, message);
    }
}
