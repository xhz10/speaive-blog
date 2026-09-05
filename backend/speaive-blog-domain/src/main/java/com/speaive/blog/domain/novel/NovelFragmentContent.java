package com.speaive.blog.domain.novel;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

/**
 * 小说片段内容值对象，维护标题、节选与 Markdown 正文；不承担发布或存储职责。
 *
 * @param title 标题
 * @param excerpt 小说片段的阅读节选
 * @param body 正文内容；格式与长度由当前业务类型约束
 */
public record NovelFragmentContent(String title, String excerpt, String body) {
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_EXCERPT_LENGTH = 500;

    public NovelFragmentContent {
        title = requireText(title, "片段标题不能为空", MAX_TITLE_LENGTH);
        excerpt = optionalText(excerpt, "片段简介不能超过 500 个字符", MAX_EXCERPT_LENGTH);
        if (body == null) {
            throw invalid("片段正文不能为空");
        }
        body = body.trim();
        if (body.indexOf('\0') >= 0) {
            throw invalid("片段正文包含非法字符");
        }
    }

    private static String requireText(String value, String emptyMessage, int maxLength) {
        String result = value == null ? "" : value.trim();
        int length = result.codePointCount(0, result.length());
        if (length == 0) {
            throw invalid(emptyMessage);
        }
        if (length > maxLength) {
            throw invalid("片段标题不能超过 " + maxLength + " 个字符");
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
