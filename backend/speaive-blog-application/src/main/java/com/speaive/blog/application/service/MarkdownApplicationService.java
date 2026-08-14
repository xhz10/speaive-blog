package com.speaive.blog.application.service;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.importing.MarkdownInboxUseCase;
import com.speaive.blog.application.port.in.markdown.MarkdownUseCase;
import com.speaive.blog.application.port.out.importing.MarkdownImportLedger;
import com.speaive.blog.application.port.out.markdown.MarkdownParseRequest;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.markdown.ParsedPostDocument;
import com.speaive.blog.application.port.out.persistence.AuthorRepository;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.importing.MarkdownImportOutcome;
import com.speaive.blog.application.result.post.PostDetailResult;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostRevisionEventType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

public final class MarkdownApplicationService implements MarkdownUseCase, MarkdownInboxUseCase {
    private static final String CONTENT_HASH_ALGORITHM = "SHA-256";

    private final PostRepository posts;
    private final AuthorRepository authors;
    private final MarkdownPort markdown;
    private final MarkdownImportLedger importLedger;
    private final TransactionRunner transactions;
    private final Clock clock;

    public MarkdownApplicationService(
            PostRepository posts,
            AuthorRepository authors,
            MarkdownPort markdown,
            MarkdownImportLedger importLedger,
            TransactionRunner transactions,
            Clock clock) {
        this.posts = Objects.requireNonNull(posts, "posts");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.markdown = Objects.requireNonNull(markdown, "markdown");
        this.importLedger = Objects.requireNonNull(importLedger, "importLedger");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PostDetailResult importDraft(String fileName, byte[] source) {
        return PostApplicationSupport.withDomainErrors(
                () -> transactions.required(() -> detail(importPost(fileName, source))));
    }

    @Override
    public MarkdownImportOutcome importOnce(String fileName, byte[] source) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            String sha256 = sha256(source);
            importLedger.lockForImport(sha256);
            if (importLedger.contains(sha256)) {
                return MarkdownImportOutcome.ALREADY_IMPORTED;
            }
            Post imported = importPost(fileName, source);
            importLedger.record(sha256, fileName, imported.slug(), clock.instant());
            return MarkdownImportOutcome.IMPORTED;
        }));
    }

    @Override
    public String preview(String source) {
        return markdown.render(source);
    }

    private Post importPost(String fileName, byte[] source) {
        Instant now = clock.instant();
        ParsedPostDocument parsed = markdown.parse(new MarkdownParseRequest(fileName, source, now));
        Post post = PostApplicationSupport.newDraft(parsed, requiredContentAuthor(), now);
        posts.add(post, PostRevisionEventType.IMPORT);
        return post;
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

    private static String sha256(byte[] source) {
        Objects.requireNonNull(source, "source");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(CONTENT_HASH_ALGORITHM).digest(source));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 " + CONTENT_HASH_ALGORITHM, exception);
        }
    }
}
