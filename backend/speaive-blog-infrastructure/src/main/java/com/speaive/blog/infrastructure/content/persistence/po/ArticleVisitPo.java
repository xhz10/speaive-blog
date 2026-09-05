package com.speaive.blog.infrastructure.content.persistence.po;

import java.time.Instant;

/** 访问事件表的一行；字段名贴合数据库，所有时间以 timestamptz 存储。 */
public record ArticleVisitPo(String id, String postId, String postSlug, String postTitle,
        Instant visitedAt, String ip, String visitorKey, String deviceType, String deviceModel,
        String operatingSystem, String browser, String location, String referrerHost) {}
