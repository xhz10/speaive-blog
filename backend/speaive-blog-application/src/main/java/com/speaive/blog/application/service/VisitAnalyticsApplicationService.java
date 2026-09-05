package com.speaive.blog.application.service;

import com.speaive.blog.application.command.analytics.RecordArticleVisitCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.analytics.VisitAnalyticsUseCase;
import com.speaive.blog.application.port.out.analytics.VisitorContextPort;
import com.speaive.blog.application.port.out.persistence.ArticleVisitRepository;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.query.analytics.VisitAnalyticsQuery;
import com.speaive.blog.application.result.analytics.VisitAnalyticsResult;
import com.speaive.blog.domain.analytics.ArticleVisit;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;

/** 公开阅读采集与管理员统计用例；统计失败由浏览器独立处理，不阻塞文章正文展示。 */
public final class VisitAnalyticsApplicationService implements VisitAnalyticsUseCase {
    private final PostRepository posts;
    private final ArticleVisitRepository visits;
    private final VisitorContextPort context;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final int retentionDays;
    private final boolean enabled;

    public VisitAnalyticsApplicationService(PostRepository posts, ArticleVisitRepository visits,
            VisitorContextPort context, TransactionRunner transactions, Clock clock, int retentionDays, boolean enabled) {
        if (retentionDays < 1 || retentionDays > 3650) throw new IllegalArgumentException("访问明细保留天数须为 1 至 3650");
        this.posts = posts; this.visits = visits; this.context = context;
        this.transactions = transactions; this.clock = clock; this.retentionDays = retentionDays; this.enabled = enabled;
    }

    @Override
    public void record(String slug, RecordArticleVisitCommand command) {
        if (!enabled) return;
        String eventId;
        try { eventId = UUID.fromString(command.eventId()).toString(); }
        catch (RuntimeException e) { throw new BlogException(BlogErrorCode.INVALID_REQUEST, "访问事件 ID 不合法"); }
        var device = context.device(command.userAgent(), command.modelHint());
        String location = context.location(command.ip());
        String key = context.visitorKey(command.ip(), command.userAgent());
        transactions.required(() -> {
            var post = posts.findBySlug(slug, PostQueryScope.PUBLISHED)
                    .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "公开文章不存在"));
            visits.add(new ArticleVisit(eventId, post.id(), post.slug(), post.content().title(),
                    clock.instant(), command.ip(), key, device, location, command.referrerHost()));
            return null;
        });
    }

    @Override
    public VisitAnalyticsResult overview(VisitAnalyticsQuery query) {
        if (query.days() < 1 || query.days() > 90 || query.page() < 1
                || query.page() > 10000 || query.pageSize() < 1 || query.pageSize() > 100
                || (query.postId() != null && query.postId().length() > 36)) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "统计范围或分页参数不合法");
        }
        var since = clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                .minusDays(Math.min(query.days(), retentionDays) - 1L).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        return transactions.required(() -> visits.overview(since, query, retentionDays));
    }

    @Override
    public void purgeExpired() {
        transactions.required(() -> { visits.deleteBefore(clock.instant().minus(Duration.ofDays(retentionDays))); return null; });
    }
}
