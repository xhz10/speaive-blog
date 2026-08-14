package com.speaive.blog.application.service;

import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.post.PostUseCase;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.markdown.ParsedPostDocument;
import com.speaive.blog.application.port.out.persistence.ArchiveReceipt;
import com.speaive.blog.application.port.out.persistence.AuthorRepository;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.post.ArchivedPostResult;
import com.speaive.blog.application.result.post.PostDetailResult;
import com.speaive.blog.application.result.post.PostListResult;
import com.speaive.blog.application.result.post.PostSummaryResult;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostChange;
import com.speaive.blog.domain.post.PostRevisionEventType;
import com.speaive.blog.domain.post.PostSlug;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class PostApplicationService implements PostUseCase {
    private final PostRepository posts;
    private final AuthorRepository authors;
    private final MarkdownPort markdown;
    private final TransactionRunner transactions;
    private final Clock clock;

    public PostApplicationService(
            PostRepository posts,
            AuthorRepository authors,
            MarkdownPort markdown,
            TransactionRunner transactions,
            Clock clock) {
        this.posts = Objects.requireNonNull(posts, "posts");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.markdown = Objects.requireNonNull(markdown, "markdown");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PostListResult listStudioPosts() {
        return PostApplicationSupport.withDomainErrors(
                () -> transactions.required(() -> list(PostQueryScope.STUDIO)));
    }

    @Override
    public PostListResult listPublishedPosts() {
        return PostApplicationSupport.withDomainErrors(
                () -> transactions.required(() -> list(PostQueryScope.PUBLISHED)));
    }

    @Override
    public PostDetailResult getStudioPost(String slug) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(
                () -> detail(requiredPost(slug, PostQueryScope.STUDIO))));
    }

    @Override
    public PostDetailResult getPublishedPost(String slug) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(
                () -> detail(requiredPost(slug, PostQueryScope.PUBLISHED))));
    }

    @Override
    public PostDetailResult createDraft(PostWriteCommand command) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            if (command == null) {
                throw new BlogException(BlogErrorCode.INVALID_REQUEST, "文章内容不能为空");
            }
            Instant now = clock.instant();
            Instant publishedAt = command.publishedAt() == null ? now : command.publishedAt();
            PostSlug slug = PostSlug.of(command.slug());
            ParsedPostDocument normalized = markdown.normalize(command, slug.value(), publishedAt);
            Post post = PostApplicationSupport.newDraft(normalized, slug, requiredContentAuthor(), now);
            posts.add(post, PostRevisionEventType.CREATE);
            return detail(post);
        }));
    }

    @Override
    public PostDetailResult update(String slug, String version, PostWriteCommand command) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            Post current = lockedPost(slug);
            current.assertVersion(version);
            Instant publishedAt = command.publishedAt() == null ? current.publishedAt() : command.publishedAt();
            ParsedPostDocument normalized = markdown.normalize(command, current.slug(), publishedAt);
            PostChange change = current.update(PostApplicationSupport.content(normalized), version, clock.instant());
            posts.save(change);
            return detail(change.current());
        }));
    }

    @Override
    public PostDetailResult publish(String slug, String version) {
        return transition(slug, version, Transition.PUBLISH);
    }

    @Override
    public PostDetailResult unpublish(String slug, String version) {
        return transition(slug, version, Transition.UNPUBLISH);
    }

    @Override
    public ArchivedPostResult archive(String slug, String version) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            Post current = lockedPost(slug);
            ArchiveReceipt archived = posts.archive(current.archive(version, clock.instant()));
            return new ArchivedPostResult(
                    archived.slug(),
                    archived.archivedFrom().name(),
                    archived.archiveReference()
            );
        }));
    }

    private PostListResult list(PostQueryScope scope) {
        List<PostSummaryResult> items = posts.findAll(scope).stream()
                .map(PostApplicationSupport::summary)
                .toList();
        return new PostListResult(items, List.of());
    }

    private PostDetailResult transition(String slug, String version, Transition transition) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            Post current = lockedPost(slug);
            PostChange change = switch (transition) {
                case PUBLISH -> current.publish(version, clock.instant());
                case UNPUBLISH -> current.unpublish(version, clock.instant());
            };
            posts.save(change);
            return detail(change.current());
        }));
    }

    private Post requiredPost(String slug, PostQueryScope scope) {
        return posts.findBySlug(PostSlug.of(slug).value(), scope)
                .orElseThrow(PostApplicationSupport::notFound);
    }

    private Post lockedPost(String slug) {
        return posts.lockBySlug(PostSlug.of(slug).value())
                .orElseThrow(PostApplicationSupport::notFound);
    }

    private Author requiredContentAuthor() {
        Author author = authors.findById(Author.ADMIN_ID)
                .orElseThrow(() -> new BlogException(BlogErrorCode.STORAGE_ERROR, "固定管理员作者不存在"));
        if (!author.canAuthor()) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "固定管理员作者已禁用");
        }
        return author;
    }

    private PostDetailResult detail(Post post) {
        return PostApplicationSupport.detail(post, markdown.render(post.body()));
    }

    private enum Transition {
        PUBLISH,
        UNPUBLISH
    }
}
