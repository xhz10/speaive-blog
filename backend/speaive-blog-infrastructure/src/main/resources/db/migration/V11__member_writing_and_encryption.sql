-- 会员身份沿用 blog_user；登录与写作设置存放在其一对一账号扩展表。
ALTER TABLE blog_account
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'READER' CHECK (role IN ('READER', 'WRITER')),
    ADD COLUMN can_publish BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN encryption_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN content_encrypted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN settings_version BIGINT NOT NULL DEFAULT 1 CHECK (settings_version > 0),
    ADD CONSTRAINT account_publish_requires_writer CHECK (NOT can_publish OR role = 'WRITER'),
    ADD CONSTRAINT account_encryption_requires_permission CHECK (NOT content_encrypted OR encryption_allowed);
COMMENT ON COLUMN blog_account.role IS '会员业务身份：READER 普通会员，WRITER 可写作作者';
COMMENT ON COLUMN blog_account.can_publish IS '管理员授予的公开发布权限';
COMMENT ON COLUMN blog_account.encryption_allowed IS '管理员是否开放加密存储资格';
COMMENT ON COLUMN blog_account.content_encrypted IS '作者是否启用加密存储；与全部文章和修订的转换在同一事务提交';
COMMENT ON COLUMN blog_account.settings_version IS '账号设置并发版本，防止旧页面覆盖新设置';

-- 复用 Post 聚合的状态机，存储区域独立，避免旧 AI/导出链路产生会员私密内容的明文副本。
CREATE TABLE blog_member_post (
    id VARCHAR(100) PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL REFERENCES blog_account(id) ON DELETE CASCADE,
    slug VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('ADMIN_ONLY', 'PUBLIC')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    archived BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT archived),
    payload TEXT NOT NULL,
    payload_encrypted BOOLEAN NOT NULL,
    UNIQUE(owner_id, slug)
);
CREATE TABLE blog_member_post_revision (
    id VARCHAR(100) NOT NULL,
    owner_id VARCHAR(100) NOT NULL REFERENCES blog_account(id) ON DELETE CASCADE,
    slug VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('ADMIN_ONLY', 'PUBLIC')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    archived BOOLEAN NOT NULL,
    payload TEXT NOT NULL,
    payload_encrypted BOOLEAN NOT NULL,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('CREATE','UPDATE','PUBLISH','UNPUBLISH','RESTORE','ARCHIVE')),
    PRIMARY KEY(id, revision)
);
CREATE INDEX member_post_owner_updated ON blog_member_post(owner_id, updated_at DESC, id);
CREATE INDEX member_revision_owner ON blog_member_post_revision(owner_id, id, revision DESC);
COMMENT ON TABLE blog_member_post IS '会员文章当前版本；访问权限必须先按 owner_id 或公开状态限定，再解密';
COMMENT ON COLUMN blog_member_post.payload IS '完整内容 JSON 或 AES-256-GCM 信封，含标题、摘要、正文、标签、封面及展示日期';
COMMENT ON COLUMN blog_member_post.payload_encrypted IS '本行是否加密，不能仅依据账号开关猜测编码';
COMMENT ON COLUMN blog_member_post.visibility IS 'ADMIN_ONLY 在会员区域表示仅文章本人可见，PUBLIC 表示允许公开';
COMMENT ON TABLE blog_member_post_revision IS '完整历史快照，包括归档文章；加密切换必须覆盖本表全部历史';
