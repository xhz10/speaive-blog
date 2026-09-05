package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.port.out.persistence.ArticleVisitRepository;
import com.speaive.blog.application.query.analytics.VisitAnalyticsQuery;
import com.speaive.blog.application.result.analytics.VisitAnalyticsResult;
import com.speaive.blog.domain.analytics.ArticleVisit;
import com.speaive.blog.infrastructure.content.persistence.mapping.ArticleVisitPersistenceMapper;
import com.speaive.blog.infrastructure.content.persistence.po.ArticleVisitPo;
import com.speaive.blog.infrastructure.content.persistence.po.VisitCountPo;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** PostgreSQL 访问日志与聚合查询；参数绑定、分页读取，不把全部访客加载到应用内存。 */
public final class PostgresArticleVisitRepository implements ArticleVisitRepository {
    private final JdbcTemplate jdbc;
    private final ArticleVisitPersistenceMapper mapping;
    public PostgresArticleVisitRepository(JdbcTemplate jdbc, ArticleVisitPersistenceMapper mapping) {
        this.jdbc = jdbc; this.mapping = mapping;
    }

    @Override
    public void add(ArticleVisit visit) {
        var p = mapping.toPo(visit);
        jdbc.update("""
            INSERT INTO blog_article_visit (id, post_id, post_slug, post_title, visited_at, ip, visitor_key,
                device_type, device_model, operating_system, browser, location, referrer_host)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
            """, p.id(), p.postId(), p.postSlug(), p.postTitle(), Timestamp.from(p.visitedAt()), p.ip(),
                p.visitorKey(), p.deviceType(), p.deviceModel(), p.operatingSystem(), p.browser(), p.location(), p.referrerHost());
    }

    @Override
    public VisitAnalyticsResult overview(Instant since, VisitAnalyticsQuery q, int retentionDays) {
        // 此条件用于所有统计和明细，使总数、图表与分页口径一致。
        String where = " WHERE visited_at >= ? AND device_type <> 'BOT'";
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(since)));
        if (q.postId() != null && !q.postId().isBlank()) { where += " AND post_id = ?"; args.add(q.postId()); }
        long[] totals = jdbc.queryForObject("SELECT count(*), count(DISTINCT visitor_key), count(DISTINCT post_id) FROM blog_article_visit" + where,
                (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(q.pageSize()); pageArgs.add((q.page() - 1) * q.pageSize());
        var items = jdbc.query("SELECT * FROM blog_article_visit" + where + " ORDER BY visited_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, n) -> mapping.toResult(new ArticleVisitPo(rs.getString("id"), rs.getString("post_id"), rs.getString("post_slug"),
                        rs.getString("post_title"), rs.getTimestamp("visited_at").toInstant(), rs.getString("ip"), rs.getString("visitor_key"),
                        rs.getString("device_type"), rs.getString("device_model"), rs.getString("operating_system"), rs.getString("browser"),
                        rs.getString("location"), rs.getString("referrer_host"))), pageArgs.toArray());
        String day = "to_char(visited_at AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM-DD')";
        return new VisitAnalyticsResult(totals[0], totals[1], totals[2], q.page(), q.pageSize(), retentionDays, items,
                counts(day, day, where, args, "key ASC", 90),
                counts("device_type", "device_type", where, args, "count DESC, key", 10),
                counts("location", "location", where, args, "count DESC, key", 10),
                counts("post_id", "(array_agg(post_title ORDER BY visited_at DESC))[1]", where, args, "count DESC, key", 10));
    }

    private List<VisitAnalyticsResult.Count> counts(String key, String label, String where,
            List<Object> args, String order, int limit) {
        // key、label、order 仅来自上面的固定 SQL，不接收客户端表达式。
        return jdbc.query("SELECT " + key + " AS key, " + label + " AS label, count(*) AS count FROM blog_article_visit"
                        + where + " GROUP BY " + key + " ORDER BY " + order + " LIMIT " + limit,
                (rs, n) -> mapping.toResult(new VisitCountPo(rs.getString("key"), rs.getString("label"), rs.getLong("count"))), args.toArray());
    }

    @Override
    public void deleteBefore(Instant cutoff) {
        jdbc.update("DELETE FROM blog_article_visit WHERE id IN (SELECT id FROM blog_article_visit WHERE visited_at < ? ORDER BY visited_at LIMIT 10000)",
                Timestamp.from(cutoff));
    }
}
