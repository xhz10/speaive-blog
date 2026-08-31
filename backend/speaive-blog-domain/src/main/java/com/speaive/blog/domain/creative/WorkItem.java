package com.speaive.blog.domain.creative;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.util.Objects;

public record WorkItem(CreativeContentType contentType, String contentSlug, int position) {
    public WorkItem {
        contentType = Objects.requireNonNull(contentType, "contentType");
        contentSlug = contentSlug == null ? "" : contentSlug.trim();
        if (contentSlug.isEmpty() || contentSlug.length() > 100) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "作品条目的 slug 不合法");
        }
        if (position < 0) throw new DomainException(DomainErrorCode.INVALID_STATE, "作品顺序不能为负数");
    }
}
