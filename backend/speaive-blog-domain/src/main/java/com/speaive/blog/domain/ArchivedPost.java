package com.speaive.blog.domain;

public record ArchivedPost(String slug, PostStatus archivedFrom, String archiveFileName) {
}
