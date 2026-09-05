package com.speaive.blog.interfaces.http.analytics;

import java.time.Instant;
import java.util.List;

/** 只向管理员序列化的统计响应，禁止复用于公开文章响应。 */
final class VisitResponses {
    private VisitResponses() {}
    record Overview(long pageViews, long visitors, long articles, int page, int pageSize, int retentionDays,
            List<Visit> items, List<Count> daily, List<Count> devices, List<Count> locations, List<Count> topArticles) {}
    /** IP 是网络出口地址，设备型号为空表示浏览器未提供。 */
    record Visit(String id, String postId, String postSlug, String postTitle, Instant visitedAt,
            String ip, String deviceType, String deviceModel, String operatingSystem, String browser, String location, String referrerHost) {}
    record Count(String key, String label, long count) {}
}
