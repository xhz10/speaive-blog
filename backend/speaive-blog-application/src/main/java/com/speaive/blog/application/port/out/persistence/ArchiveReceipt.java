package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.post.PostStatus;

public record ArchiveReceipt(String slug, PostStatus archivedFrom, String archiveReference) {
}
