package com.speaive.blog.application.query.analytics;

/** 最近 days 天的统计，按北京时间自然日计算；page 从 1 开始，pageSize 最大 100。 */
public record VisitAnalyticsQuery(int days, int page, int pageSize, String postId) {}
