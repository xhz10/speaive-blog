package com.speaive.blog.domain.post;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 文章公开路径标识的值对象，负责长度与字符规则。slug 可在归档后重用，所以它不能替代文章的稳定 ID。
 *
 * @param value 经过校验的路径标识文本
 */
public record PostSlug(String value) {
    public static final int MAX_LENGTH = 100;
    private static final Pattern VALID_SLUG = Pattern.compile(
            "^[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*$");

    public PostSlug {
        if (value == null) {
            throw invalid("文章缺少 slug");
        }
        value = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        int length = value.codePointCount(0, value.length());
        if (length == 0 || length > MAX_LENGTH || !VALID_SLUG.matcher(value).matches()) {
            throw invalid("slug 只允许文字、数字和单个连字符，且不能超过 100 个字符");
        }
    }

    public static PostSlug of(String value) {
        return new PostSlug(value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_SLUG, message);
    }
}
