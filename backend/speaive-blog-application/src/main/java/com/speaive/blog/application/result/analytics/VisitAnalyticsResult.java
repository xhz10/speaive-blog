package com.speaive.blog.application.result.analytics;

import java.time.Instant;
import java.util.List;

/** 管理员统计读模型；聚合排除已识别的机器人，所有列表均受查询范围约束。 */
public record VisitAnalyticsResult(long pageViews, long visitors, long articles,
        int page, int pageSize, int retentionDays, List<Visit> items,
        List<Count> daily, List<Count> devices, List<Count> locations, List<Count> topArticles) {
    /** 访问明细中的时间为 UTC 瞬时值，前端按北京时间展示。 */
    public record Visit(String id, String postId, String postSlug, String postTitle, Instant visitedAt,
            String ip, String deviceType, String deviceModel, String operatingSystem,
            String browser, String location, String referrerHost) {}
    /** 统计分组；key 用于筛选，label 用于显示，count 表示访问次数。 */
    public record Count(String key, String label, long count) {}
}
