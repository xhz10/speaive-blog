package com.speaive.blog.application.service;

import com.speaive.blog.application.command.novel.NovelFragmentWriteCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.novel.NovelFragmentUseCase;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.persistence.AuthorRepository;
import com.speaive.blog.application.port.out.persistence.NovelFragmentQueryScope;
import com.speaive.blog.application.port.out.persistence.NovelFragmentRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.novel.NovelFragmentDetailResult;
import com.speaive.blog.application.result.novel.NovelFragmentListResult;
import com.speaive.blog.application.result.novel.NovelFragmentSummaryResult;
import com.speaive.blog.application.result.post.AuthorResult;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.novel.NovelFragment;
import com.speaive.blog.domain.novel.NovelFragmentChange;
import com.speaive.blog.domain.novel.NovelFragmentContent;
import com.speaive.blog.domain.novel.NovelFragmentRevisionEventType;
import com.speaive.blog.domain.novel.NovelFragmentSlug;
import com.speaive.blog.domain.novel.NovelFragmentSummary;
import com.speaive.blog.domain.novel.NovelFragmentVisibility;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class NovelFragmentApplicationService implements NovelFragmentUseCase {
    private final NovelFragmentRepository fragments;
    private final AuthorRepository authors;
    private final MarkdownPort markdown;
    private final TransactionRunner transactions;
    private final Clock clock;

    public NovelFragmentApplicationService(
            NovelFragmentRepository fragments,
            AuthorRepository authors,
            MarkdownPort markdown,
            TransactionRunner transactions,
            Clock clock) {
        this.fragments = Objects.requireNonNull(fragments, "fragments");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.markdown = Objects.requireNonNull(markdown, "markdown");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public NovelFragmentListResult listStudioFragments() {
        return PostApplicationSupport.withDomainErrors(
                () -> transactions.required(() -> list(NovelFragmentQueryScope.STUDIO)));
    }

    @Override
    public NovelFragmentListResult listPublishedFragments() {
        return PostApplicationSupport.withDomainErrors(
                () -> transactions.required(() -> list(NovelFragmentQueryScope.PUBLISHED)));
    }

    @Override
    public NovelFragmentDetailResult getStudioFragment(String slug) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(
                () -> detail(requiredFragment(slug, NovelFragmentQueryScope.STUDIO))));
    }

    @Override
    public NovelFragmentDetailResult getPublishedFragment(String slug) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(
                () -> detail(requiredFragment(slug, NovelFragmentQueryScope.PUBLISHED))));
    }

    @Override
    public NovelFragmentDetailResult createDraft(NovelFragmentWriteCommand command) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            requireCommand(command);
            NovelFragment fragment = NovelFragment.createDraft(
                    UUID.randomUUID().toString(),
                    NovelFragmentSlug.of(command.slug()),
                    content(command),
                    requiredContentAuthor(),
                    visibility(command),
                    clock.instant()
            );
            fragments.add(fragment, NovelFragmentRevisionEventType.CREATE);
            return detail(fragment);
        }));
    }

    @Override
    public NovelFragmentDetailResult update(
            String slug,
            String version,
            NovelFragmentWriteCommand command) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            requireCommand(command);
            NovelFragment current = lockedFragment(slug);
            NovelFragmentChange change = current.update(
                    content(command), visibility(command), version, clock.instant());
            fragments.save(change);
            return detail(change.current());
        }));
    }

    @Override
    public NovelFragmentDetailResult publish(String slug, String version) {
        return transition(slug, version, true);
    }

    @Override
    public NovelFragmentDetailResult unpublish(String slug, String version) {
        return transition(slug, version, false);
    }

    private NovelFragmentDetailResult transition(String slug, String version, boolean publish) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            NovelFragment current = lockedFragment(slug);
            NovelFragmentChange change = publish
                    ? current.publish(version, clock.instant())
                    : current.unpublish(version, clock.instant());
            fragments.save(change);
            return detail(change.current());
        }));
    }

    private NovelFragmentListResult list(NovelFragmentQueryScope scope) {
        List<NovelFragmentSummaryResult> items = fragments.findAll(scope).stream()
                .map(this::summary)
                .toList();
        return new NovelFragmentListResult(items);
    }

    private NovelFragment requiredFragment(String slug, NovelFragmentQueryScope scope) {
        return fragments.findBySlug(NovelFragmentSlug.of(slug).value(), scope)
                .orElseThrow(this::notFound);
    }

    private NovelFragment lockedFragment(String slug) {
        return fragments.lockBySlug(NovelFragmentSlug.of(slug).value())
                .orElseThrow(this::notFound);
    }

    private Author requiredContentAuthor() {
        Author author = authors.findById(Author.ADMIN_ID)
                .orElseThrow(() -> new BlogException(BlogErrorCode.STORAGE_ERROR, "固定管理员作者不存在"));
        if (!author.canAuthor()) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "固定管理员作者已禁用");
        }
        return author;
    }

    private NovelFragmentDetailResult detail(NovelFragment fragment) {
        return new NovelFragmentDetailResult(
                fragment.slug(), fragment.title(), fragment.excerpt(), fragment.publishedAt(), fragment.updatedAt(),
                author(fragment.author()), fragment.status().name(), fragment.visibility().name(),
                fragment.body(), markdown.render(fragment.body()), fragment.version()
        );
    }

    private NovelFragmentSummaryResult summary(NovelFragmentSummary fragment) {
        return new NovelFragmentSummaryResult(
                fragment.slugText(), fragment.title(), fragment.excerpt(), fragment.publishedAt(), fragment.updatedAt(),
                author(fragment.author()), fragment.status().name(), fragment.visibility().name(), fragment.version()
        );
    }

    private static NovelFragmentContent content(NovelFragmentWriteCommand command) {
        return new NovelFragmentContent(command.title(), command.excerpt(), command.body());
    }

    private static void requireCommand(NovelFragmentWriteCommand command) {
        if (command == null) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "小说片段内容不能为空");
        }
    }

    private static NovelFragmentVisibility visibility(NovelFragmentWriteCommand command) {
        try {
            return NovelFragmentVisibility.valueOf(command.visibility());
        } catch (IllegalArgumentException exception) {
            throw new BlogException(
                    BlogErrorCode.INVALID_REQUEST,
                    "小说片段可见性只能是 PUBLIC 或 ADMIN_ONLY",
                    exception);
        }
    }

    private static AuthorResult author(Author author) {
        return new AuthorResult(
                author.id(), author.username(), author.displayName(), author.type().name(), author.avatarUrl());
    }

    private BlogException notFound() {
        return new BlogException(BlogErrorCode.NOT_FOUND, "小说片段不存在");
    }
}
