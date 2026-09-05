package com.speaive.blog.application.command.analytics;

/** 访问采集命令；ip 和 userAgent 由 HTTP 入口读取，浏览器只提交事件 ID、型号提示及来源站点。 */
public record RecordArticleVisitCommand(String eventId, String ip, String userAgent,
        String modelHint, String referrerHost) {}
