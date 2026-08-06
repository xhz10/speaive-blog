package com.speaive.blog.interfaces.http;

import com.speaive.blog.domain.AuthorType;
import com.speaive.blog.domain.ContentError;
import com.speaive.blog.domain.Post;
import com.speaive.blog.domain.PostCollection;
import com.speaive.blog.domain.PostStatus;

import java.time.Instant;
import java.util.List;

final class PostResponses {
    private PostResponses() {
    }

    static PostDetail detail(Post post) {
        return new PostDetail(post.slug(), post.title(), post.description(), post.publishedAt(), post.updatedAt(),
                post.tags(), post.cover(), author(post), post.status(), post.body(), post.html(), post.version());
    }

    static PostList list(PostCollection collection) {
        List<PostSummary> items = collection.items().stream().map(PostResponses::summary).toList();
        List<ContentScanError> errors = collection.errors().stream().map(PostResponses::error).toList();
        return new PostList(items, errors);
    }

    private static PostSummary summary(Post post) {
        return new PostSummary(post.slug(), post.title(), post.description(), post.publishedAt(), post.updatedAt(),
                post.tags(), post.cover(), author(post), post.status(), post.version());
    }

    private static AuthorSummary author(Post post) {
        return new AuthorSummary(post.author().id(), post.author().username(), post.author().displayName(),
                post.author().type(), post.author().avatarUrl());
    }

    private static ContentScanError error(ContentError error) {
        return new ContentScanError(error.file(), error.status(), error.message());
    }

    record PostDetail(
            String slug,
            String title,
            String description,
            Instant publishedAt,
            Instant updatedAt,
            List<String> tags,
            String cover,
            AuthorSummary author,
            PostStatus status,
            String body,
            String html,
            String version
    ) {
    }

    record PostSummary(
            String slug,
            String title,
            String description,
            Instant publishedAt,
            Instant updatedAt,
            List<String> tags,
            String cover,
            AuthorSummary author,
            PostStatus status,
            String version
    ) {
    }

    record AuthorSummary(String id, String username, String displayName, AuthorType type, String avatarUrl) {
    }

    record ContentScanError(String file, PostStatus status, String message) {
    }

    record PostList(List<PostSummary> items, List<ContentScanError> errors) {
    }
}
