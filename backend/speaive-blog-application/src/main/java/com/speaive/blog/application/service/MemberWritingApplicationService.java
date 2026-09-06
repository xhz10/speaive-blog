package com.speaive.blog.application.service;

import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.error.*;
import com.speaive.blog.application.port.in.post.MemberWritingUseCase;
import com.speaive.blog.application.port.out.persistence.*;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.post.*;
import com.speaive.blog.domain.account.*;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.*;
import java.time.Clock;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * 会员写作用例。所有权来自认证用户名，仓储查询始终限定 ownerId；每次操作重新读取账号资格。
 * 与站长文章共用 Post 生命周期，但不进入旧 AI、导出和站长公开列表等会产生文本副本的链路。
 */
public final class MemberWritingApplicationService implements MemberWritingUseCase {
    private static final int PAGE_SIZE = 20;
    private final AccountRepository accounts;
    private final MemberPostRepository posts;
    private final MarkdownPort markdown;
    private final TransactionRunner transactions;
    private final Clock clock;

    public MemberWritingApplicationService(AccountRepository accounts, MemberPostRepository posts, MarkdownPort markdown,
            TransactionRunner transactions, Clock clock) {
        this.accounts = accounts; this.posts = posts; this.markdown = markdown;
        this.transactions = transactions; this.clock = clock;
    }

    @Override
    public MemberPostListResult listOwn(String username, int page) { return list(account(username, false), false, page); }

    @Override
    public PostDetailResult getOwn(String username, String slug) {
        return detail(own(account(username, false), slug));
    }

    @Override
    public PostDetailResult create(String username, PostWriteCommand command) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            MemberAccount account = account(username, true);
            requireWrite(account);
            // URL 使用随机标识，私密标题不会因为 slug 泄露；创建一律为草稿。
            String id = UUID.randomUUID().toString();
            var document = markdown.normalize(command, id, clock.instant());
            checkMedia(document.body(), document.cover());
            Post post = Post.createDraft(id, id, PostApplicationSupport.content(document), account.identity(),
                    document.visibility(), clock.instant());
            posts.add(post, account.contentEncrypted());
            return detail(post);
        }));
    }

    @Override
    public PostDetailResult update(String username, String slug, String version, PostWriteCommand command) {
        return change(username, slug, (account, post) -> {
            requireWrite(account);
            var document = markdown.normalize(command, post.slug(), post.publishedAt());
            checkMedia(document.body(), document.cover());
            if (post.status() == PostStatus.PUBLISHED && document.visibility() == PostVisibility.PUBLIC) requirePublish(account);
            return post.update(PostApplicationSupport.content(document), document.visibility(), version, clock.instant());
        });
    }

    @Override
    public PostDetailResult publish(String username, String slug, String version) {
        return change(username, slug, (account, post) -> {
            requirePublish(account);
            if (post.visibility() != PostVisibility.PUBLIC) {
                throw new BlogException(BlogErrorCode.INVALID_REQUEST, "请先将可见范围设置为公开，再发布文章");
            }
            return post.publish(version, clock.instant());
        });
    }

    @Override
    public PostDetailResult unpublish(String username, String slug, String version) {
        // 撤销写作或发布资格后，作者仍能主动撤回自己已经公开的内容。
        return change(username, slug, (account, post) -> post.unpublish(version, clock.instant()));
    }

    @Override
    public void archive(String username, String slug, String version) {
        change(username, slug, (account, post) -> post.archive(version, clock.instant()));
    }

    @Override
    public MemberPostHistoryResult history(String username, String slug) {
        MemberAccount account = account(username, false);
        Post post = own(account, slug);
        return new MemberPostHistoryResult(posts.revisions(account.id(), post.id()).stream()
                .map(item -> new MemberPostHistoryResult.Item(item.revision(), item.title(), item.updatedAt(), item.status().name())).toList());
    }

    @Override
    public PostDetailResult restore(String username, String slug, long revision, String version) {
        return change(username, slug, (account, post) -> {
            requireWrite(account);
            Post historical = posts.revision(account.id(), post.id(), revision).orElseThrow(PostApplicationSupport::notFound);
            if (post.status() == PostStatus.PUBLISHED && historical.visibility() == PostVisibility.PUBLIC) requirePublish(account);
            return post.restore(historical.content(), historical.visibility(), version, clock.instant());
        });
    }

    @Override
    public MemberPostListResult profile(String username, int page) { return list(publicAuthor(username), true, page); }

    @Override
    public PostDetailResult published(String username, String slug) {
        MemberAccount account = publicAuthor(username);
        return detail(posts.find(account.id(), slug, true).orElseThrow(PostApplicationSupport::notFound));
    }

    private PostDetailResult change(String username, String slug, BiFunction<MemberAccount, Post, PostChange> action) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            MemberAccount account = account(username, true);
            PostChange change = action.apply(account, own(account, slug));
            posts.save(change, account.contentEncrypted());
            return detail(change.current());
        }));
    }

    private MemberPostListResult list(MemberAccount account, boolean published, int page) {
        if (page < 1 || page > 100_000) throw new BlogException(BlogErrorCode.INVALID_REQUEST, "页码不合法");
        var items = posts.list(account.id(), published, page, PAGE_SIZE).stream().map(this::summary).toList();
        return new MemberPostListResult(account.identity().username(), account.identity().displayName(),
                posts.count(account.id(), published), page, PAGE_SIZE, items);
    }

    private MemberAccount publicAuthor(String username) {
        return accounts.findByUsername(username).filter(MemberAccount::enabled).filter(value -> value.role() == MemberRole.WRITER)
                .orElseThrow(PostApplicationSupport::notFound);
    }

    private MemberAccount account(String username, boolean lock) {
        return (lock ? accounts.lockByUsername(username) : accounts.findByUsername(username))
                .filter(MemberAccount::enabled).orElseThrow(PostApplicationSupport::notFound);
    }

    private Post own(MemberAccount account, String slug) {
        return posts.find(account.id(), slug, false).orElseThrow(PostApplicationSupport::notFound);
    }

    private static void requireWrite(MemberAccount account) {
        try { account.ensureCanWrite(); }
        catch (DomainException exception) { throw new BlogException(BlogErrorCode.FORBIDDEN, exception.getMessage()); }
    }

    private static void requirePublish(MemberAccount account) {
        try { account.ensureCanPublish(); }
        catch (DomainException exception) { throw new BlogException(BlogErrorCode.FORBIDDEN, exception.getMessage()); }
    }

    private void checkMedia(String body, String cover) {
        if (!markdown.referencedMediaPaths(body, cover).isEmpty()) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "会员文章暂不支持站内媒体上传，请使用文字或公开外链图片");
        }
    }

    private PostDetailResult detail(Post post) { return PostApplicationSupport.detail(post, markdown.render(post.body())); }

    private PostSummaryResult summary(Post post) {
        return PostApplicationSupport.summary(new PostSummary(post.id(), post.slugValue(), post.title(), post.description(),
                post.publishedAt(), post.updatedAt(), post.tags(), post.cover(), post.author(), post.status(), post.visibility(), post.revision()));
    }
}
