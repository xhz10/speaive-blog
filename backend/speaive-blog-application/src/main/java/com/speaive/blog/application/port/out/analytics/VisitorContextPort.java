package com.speaive.blog.application.port.out.analytics;

import com.speaive.blog.domain.analytics.VisitorDevice;

/** 设备解析、离线 IP 归属地与去重摘要的技术适配边界；查询失败时地点返回“未知”。 */
public interface VisitorContextPort {
    VisitorDevice device(String userAgent, String modelHint);
    String location(String ip);
    String visitorKey(String ip, String userAgent);
}
