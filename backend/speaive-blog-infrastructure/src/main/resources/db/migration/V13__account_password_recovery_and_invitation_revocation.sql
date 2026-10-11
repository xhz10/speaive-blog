-- 独立凭证版本避免换密覆盖权限与加密设置，现有会员初始版本统一回填为 1。
ALTER TABLE blog_account ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 1 CHECK (credential_version > 0);
COMMENT ON COLUMN blog_account.credential_version IS '密码凭证版本；换密后使旧会话和旧找回令牌失效';
ALTER TABLE blog_invitation
    ADD COLUMN note VARCHAR(200),
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD CONSTRAINT invitation_revocation_time CHECK (revoked_at IS NULL OR revoked_at >= created_at);
COMMENT ON COLUMN blog_invitation.note IS '站长可选管理备注，不参与邀请码校验';
COMMENT ON COLUMN blog_invitation.revoked_at IS '撤销时间；撤销与注册共享行锁，不影响已经注册的会员';
CREATE TABLE blog_password_reset (
    token_hash CHAR(64) PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    account_id VARCHAR(100) NOT NULL REFERENCES blog_account(id) ON DELETE CASCADE,
    credential_version BIGINT NOT NULL CHECK (credential_version > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CHECK (expires_at > created_at),
    CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);
CREATE INDEX password_reset_account ON blog_password_reset(account_id);
CREATE UNIQUE INDEX password_reset_one_active_per_account ON blog_password_reset(account_id) WHERE consumed_at IS NULL;
COMMENT ON TABLE blog_password_reset IS '一次性密码找回凭证；只保存 SHA-256 哈希，原始 256 位随机令牌仅在签发响应中展示';
COMMENT ON COLUMN blog_password_reset.credential_version IS '签发时的密码版本；换密立即拒绝旧凭证';
COMMENT ON COLUMN blog_password_reset.consumed_at IS '原子消费或重发撤销的时间；到期凭证也不得再次消费';
