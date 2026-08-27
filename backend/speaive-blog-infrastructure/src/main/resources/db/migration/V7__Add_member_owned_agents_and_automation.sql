CREATE TABLE blog_account (
    id VARCHAR(36) PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT blog_account_user_fk FOREIGN KEY (id)
        REFERENCES blog_user (id) ON DELETE CASCADE,
    CONSTRAINT blog_account_password_hash_not_blank CHECK (BTRIM(password_hash) <> '')
);

CREATE TABLE blog_invitation (
    id VARCHAR(36) PRIMARY KEY,
    code_hash CHAR(64) NOT NULL UNIQUE,
    max_uses INTEGER NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT blog_invitation_usage_check CHECK (
        max_uses BETWEEN 1 AND 100 AND used_count BETWEEN 0 AND max_uses
    ),
    CONSTRAINT blog_invitation_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX blog_invitation_recent_idx
    ON blog_invitation (created_at DESC, id);

ALTER TABLE blog_agent
    ADD COLUMN owner_account_id VARCHAR(36),
    ADD COLUMN enabled_requested BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN review_note VARCHAR(500),
    ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN auto_comment_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN auto_comment_all_posts BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE blog_agent agent
SET enabled_requested = (author.status = 'ACTIVE'),
    reviewed_at = agent.created_at
FROM blog_user author
WHERE author.id = agent.id;

ALTER TABLE blog_agent
    ADD CONSTRAINT blog_agent_owner_fk FOREIGN KEY (owner_account_id)
        REFERENCES blog_account (id) ON DELETE RESTRICT,
    ADD CONSTRAINT blog_agent_review_status_check CHECK (
        review_status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    ADD CONSTRAINT blog_agent_owner_private_check CHECK (
        owner_account_id IS NULL OR (can_process_private = FALSE AND model IS NULL)
    ),
    ADD CONSTRAINT blog_agent_site_review_check CHECK (
        owner_account_id IS NOT NULL OR review_status = 'APPROVED'
    ),
    ADD CONSTRAINT blog_agent_review_result_check CHECK (
        (review_status = 'PENDING' AND review_note IS NULL AND reviewed_at IS NULL)
        OR (review_status = 'APPROVED' AND review_note IS NULL AND reviewed_at IS NOT NULL)
        OR (review_status = 'REJECTED' AND review_note IS NOT NULL AND reviewed_at IS NOT NULL)
    );

CREATE INDEX blog_agent_owner_idx
    ON blog_agent (owner_account_id, created_at, id)
    WHERE owner_account_id IS NOT NULL;

CREATE INDEX blog_agent_pending_review_idx
    ON blog_agent (created_at, id)
    WHERE review_status = 'PENDING';

CREATE TABLE blog_agent_auto_tag (
    agent_id VARCHAR(36) NOT NULL,
    tag VARCHAR(40) NOT NULL,
    tag_order INTEGER NOT NULL,
    PRIMARY KEY (agent_id, tag_order),
    CONSTRAINT blog_agent_auto_tag_unique UNIQUE (agent_id, tag),
    CONSTRAINT blog_agent_auto_tag_agent_fk FOREIGN KEY (agent_id)
        REFERENCES blog_agent (id) ON DELETE CASCADE
);

CREATE TABLE blog_post_community_policy (
    post_id VARCHAR(36) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT blog_post_community_policy_post_fk FOREIGN KEY (post_id)
        REFERENCES blog_post (id) ON DELETE CASCADE,
    CONSTRAINT blog_post_community_policy_version_check CHECK (version > 0)
);

CREATE TABLE blog_community_comment_job (
    id VARCHAR(36) PRIMARY KEY,
    post_id VARCHAR(36) NOT NULL,
    post_revision BIGINT NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT blog_community_comment_job_post_fk FOREIGN KEY (post_id)
        REFERENCES blog_post (id) ON DELETE CASCADE,
    CONSTRAINT blog_community_comment_job_agent_fk FOREIGN KEY (agent_id)
        REFERENCES blog_agent (id) ON DELETE RESTRICT,
    CONSTRAINT blog_community_comment_job_status_check CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'SKIPPED', 'FAILED')
    ),
    CONSTRAINT blog_community_comment_job_revision_check CHECK (post_revision > 0),
    CONSTRAINT blog_community_comment_job_attempts_check CHECK (attempts >= 0),
    CONSTRAINT blog_community_comment_job_unique UNIQUE (post_id, post_revision, agent_id)
);

CREATE INDEX blog_community_comment_job_due_idx
    ON blog_community_comment_job (available_at, created_at, id)
    WHERE status = 'PENDING';

CREATE INDEX blog_community_comment_job_running_idx
    ON blog_community_comment_job (claimed_at, id)
    WHERE status = 'RUNNING';
