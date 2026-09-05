package com.speaive.blog.application.port.in.analytics;

import com.speaive.blog.application.command.analytics.RecordArticleVisitCommand;
import com.speaive.blog.application.query.analytics.VisitAnalyticsQuery;
import com.speaive.blog.application.result.analytics.VisitAnalyticsResult;

/** 访客统计入口；采集对匿名读者开放，查询仅供管理员，清理由定时入口调用。 */
public interface VisitAnalyticsUseCase {
    void record(String slug, RecordArticleVisitCommand command);
    VisitAnalyticsResult overview(VisitAnalyticsQuery query);
    void purgeExpired();
}
