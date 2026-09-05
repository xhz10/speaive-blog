package com.speaive.blog.domain.analytics;

import java.time.Instant;
import java.util.Objects;

/**
 * 一次公开文章的阅读事件，写入后不再修改；不是注册用户，也不推断访客真实身份。
 * 文章 ID 是稳定标识，标题及 slug 是访问当时的快照，文章归档后仍保留统计。
 * visitorKey 用于估算去重（同 IP 与浏览器视作一个访客），id 用于网络重试去重。
 */
public record ArticleVisit(String id, String postId, String postSlug, String postTitle,
        Instant visitedAt, String ip, String visitorKey, VisitorDevice device,
        String location, String referrerHost) {
    public ArticleVisit {
        Objects.requireNonNull(id, "访问事件 ID");
        Objects.requireNonNull(postId, "文章 ID");
        Objects.requireNonNull(visitedAt, "服务端访问时间");
        Objects.requireNonNull(device, "设备信息");
    }
}
