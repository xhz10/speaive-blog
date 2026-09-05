package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.application.query.analytics.VisitAnalyticsQuery;
import com.speaive.blog.application.result.analytics.VisitAnalyticsResult;
import com.speaive.blog.domain.analytics.ArticleVisit;
import java.time.Instant;

/** 访问事件与统计查询的持久化端口，不允许从公开 API 读取明细。 */
public interface ArticleVisitRepository {
    /** 按事件 ID 幂等插入；同一个页面的重试不增加访问次数。 */
    void add(ArticleVisit visit);
    VisitAnalyticsResult overview(Instant since, VisitAnalyticsQuery query, int retentionDays);
    /** 分批清理过期明细，避免一次持有过多行锁。 */
    void deleteBefore(Instant cutoff);
}
