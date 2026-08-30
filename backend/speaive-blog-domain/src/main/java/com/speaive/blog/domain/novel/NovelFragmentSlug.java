package com.speaive.blog.domain.novel;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.text.Normalizer;
import java.util.regex.Pattern;

public record NovelFragmentSlug(String value) {
    public static final int MAX_LENGTH = 100;
    private static final Pattern VALID_SLUG = Pattern.compile(
            "^[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*$");

    public NovelFragmentSlug {
        if (value == null) {
            throw invalid("小说片段缺少 slug");
        }
        value = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        int length = value.codePointCount(0, value.length());
        if (length == 0 || length > MAX_LENGTH || !VALID_SLUG.matcher(value).matches()) {
            throw invalid("slug 只允许文字、数字和单个连字符，且不能超过 100 个字符");
        }
    }

    public static NovelFragmentSlug of(String value) {
        return new NovelFragmentSlug(value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_SLUG, message);
    }
}
