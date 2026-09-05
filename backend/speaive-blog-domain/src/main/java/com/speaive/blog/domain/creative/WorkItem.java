package com.speaive.blog.domain.creative;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.util.Objects;

/**
 * 作品集内的内容引用值对象，用内容类型和 slug 定位文章或小说，以 position 决定阅读顺序。
 *
 * @param contentType 引用内容的业务类型：文章或小说片段
 * @param contentSlug 被引用内容的路径标识
 * @param position 条目在作品集中的位置，从 0 开始且连续
 */
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
