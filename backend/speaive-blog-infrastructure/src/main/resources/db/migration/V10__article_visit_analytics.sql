-- 访问日志不外键级联文章：归档或重用 slug 后，历史访问仍属于原文章 ID。
CREATE TABLE blog_article_visit (
    id VARCHAR(36) PRIMARY KEY,
    post_id VARCHAR(36) NOT NULL,
    post_slug VARCHAR(160) NOT NULL,
    post_title TEXT NOT NULL,
    visited_at TIMESTAMPTZ NOT NULL,
    ip VARCHAR(45) NOT NULL,
    visitor_key VARCHAR(64) NOT NULL,
    device_type VARCHAR(16) NOT NULL CHECK (device_type IN ('MOBILE', 'TABLET', 'DESKTOP', 'BOT', 'UNKNOWN')),
    device_model VARCHAR(120) NOT NULL,
    operating_system VARCHAR(80) NOT NULL,
    browser VARCHAR(80) NOT NULL,
    location VARCHAR(200) NOT NULL,
    referrer_host VARCHAR(253) NOT NULL
);
CREATE INDEX idx_article_visit_time ON blog_article_visit (visited_at DESC, id DESC);
CREATE INDEX idx_article_visit_post_time ON blog_article_visit (post_id, visited_at DESC);
COMMENT ON TABLE blog_article_visit IS '匿名公开文章访问明细，仅管理员可查询，按配置保留期限清理';
COMMENT ON COLUMN blog_article_visit.id IS '浏览器单次页面事件 ID，用于重试幂等去重';
COMMENT ON COLUMN blog_article_visit.ip IS '由服务端及可信代理取得的来源 IP，可能是共享出口或代理';
COMMENT ON COLUMN blog_article_visit.visitor_key IS 'IP 与用户代理的 SHA-256 摘要，用于估算访客数，不等于自然人数';
COMMENT ON COLUMN blog_article_visit.location IS '离线 IP 数据库解析的粗略归属地，不是 GPS 定位';
COMMENT ON COLUMN blog_article_visit.device_model IS '浏览器提供的型号提示，空值表示未提供，不能据此识别个人';
