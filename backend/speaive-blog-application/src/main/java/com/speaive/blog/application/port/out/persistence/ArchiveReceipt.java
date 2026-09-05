package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.post.PostStatus;

/**
 * 文章归档持久化完成后的回执，供应用层转换响应，不代替 PostChange 中的归档规则。
 */
public record ArchiveReceipt(String slug, PostStatus archivedFrom, String archiveReference) {
}
