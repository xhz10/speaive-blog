package com.speaive.blog.application.service;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.markdown.ParsedPostDocument;
import com.speaive.blog.application.result.post.AuthorResult;
import com.speaive.blog.application.result.post.PostDetailResult;
import com.speaive.blog.application.result.post.PostSummaryResult;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostContent;
import com.speaive.blog.domain.post.PostSlug;
import com.speaive.blog.domain.post.PostSummary;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

final class PostApplicationSupport {
    private PostApplicationSupport() {
    }

    static Post newDraft(ParsedPostDocument document, Author author, Instant now) {
        return newDraft(document, PostSlug.of(document.slug()), author, now);
    }

    static Post newDraft(ParsedPostDocument document, PostSlug slug, Author author, Instant now) {
        return Post.createDraft(
                UUID.randomUUID().toString(),
                slug,
                content(document),
                author,
                document.visibility(),
                now
        );
    }

    static PostContent content(ParsedPostDocument document) {
        return new PostContent(
                document.title(),
                document.description(),
                document.publishedAt(),
                document.tags(),
                document.cover(),
                document.body()
        );
    }

    static PostDetailResult detail(Post post, String html) {
        return new PostDetailResult(
                post.slug(),
                post.title(),
                post.description(),
                post.publishedAt(),
                post.updatedAt(),
                post.tags(),
                post.cover(),
                author(post.author()),
                post.status().name(),
                post.visibility().name(),
                post.body(),
                html,
                post.version()
        );
    }

    static PostSummaryResult summary(PostSummary post) {
        return new PostSummaryResult(
                post.slugText(),
                post.title(),
                post.description(),
                post.publishedAt(),
                post.updatedAt(),
                post.tags(),
                post.cover(),
                author(post.author()),
                post.status().name(),
                post.visibility().name(),
                post.version()
        );
    }

    static BlogException notFound() {
        return new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在");
    }

    static <T> T withDomainErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (DomainException exception) {
            throw new BlogException(toApplicationCode(exception.code()), exception.getMessage(), exception);
        }
    }

    private static AuthorResult author(Author author) {
        return new AuthorResult(
                author.id(),
                author.username(),
                author.displayName(),
                author.type().name(),
                author.avatarUrl()
        );
    }

    private static BlogErrorCode toApplicationCode(DomainErrorCode code) {
        return switch (code) {
            case INVALID_SLUG -> BlogErrorCode.INVALID_FILE_NAME;
            case INVALID_CONTENT -> BlogErrorCode.INVALID_MARKDOWN;
            case INVALID_STATE, INVALID_AUTHOR, INVALID_AGENT, INVALID_COMMENT -> BlogErrorCode.INVALID_REQUEST;
            case VERSION_CONFLICT -> BlogErrorCode.VERSION_CONFLICT;
        };
    }
}
