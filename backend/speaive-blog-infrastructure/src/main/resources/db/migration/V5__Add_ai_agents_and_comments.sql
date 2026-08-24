CREATE TABLE blog_agent (
    id VARCHAR(36) PRIMARY KEY,
    system_prompt TEXT NOT NULL,
    model VARCHAR(120),
    temperature NUMERIC(3, 2) NOT NULL DEFAULT 0.70,
    can_process_private BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT blog_agent_user_fk FOREIGN KEY (id)
        REFERENCES blog_user (id) ON DELETE CASCADE,
    CONSTRAINT blog_agent_prompt_not_blank CHECK (BTRIM(system_prompt) <> ''),
    CONSTRAINT blog_agent_prompt_length CHECK (CHAR_LENGTH(system_prompt) <= 12000),
    CONSTRAINT blog_agent_model_not_blank CHECK (model IS NULL OR BTRIM(model) <> ''),
    CONSTRAINT blog_agent_temperature_check CHECK (temperature >= 0 AND temperature <= 2),
    CONSTRAINT blog_agent_prompt_version_check CHECK (prompt_version > 0)
);

CREATE TABLE blog_comment (
    id VARCHAR(36) PRIMARY KEY,
    post_id VARCHAR(36) NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT blog_comment_post_fk FOREIGN KEY (post_id)
        REFERENCES blog_post (id) ON DELETE CASCADE,
    CONSTRAINT blog_comment_author_fk FOREIGN KEY (author_id)
        REFERENCES blog_user (id) ON DELETE RESTRICT,
    CONSTRAINT blog_comment_body_not_blank CHECK (BTRIM(body) <> ''),
    CONSTRAINT blog_comment_body_length CHECK (CHAR_LENGTH(body) <= 2000),
    CONSTRAINT blog_comment_status_check CHECK (status IN ('PENDING', 'PUBLISHED', 'HIDDEN'))
);

CREATE INDEX blog_comment_post_status_order_idx
    ON blog_comment (post_id, status, created_at, id);

CREATE TABLE blog_agent_run (
    id VARCHAR(36) PRIMARY KEY,
    post_id VARCHAR(36) NOT NULL,
    post_revision BIGINT NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    prompt_version BIGINT NOT NULL,
    model VARCHAR(120),
    status VARCHAR(20) NOT NULL,
    comment_id VARCHAR(36),
    input_tokens INTEGER,
    output_tokens INTEGER,
    error_message VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT blog_agent_run_post_fk FOREIGN KEY (post_id)
        REFERENCES blog_post (id) ON DELETE CASCADE,
    CONSTRAINT blog_agent_run_agent_fk FOREIGN KEY (agent_id)
        REFERENCES blog_agent (id) ON DELETE RESTRICT,
    CONSTRAINT blog_agent_run_comment_fk FOREIGN KEY (comment_id)
        REFERENCES blog_comment (id) ON DELETE SET NULL,
    CONSTRAINT blog_agent_run_revision_check CHECK (post_revision > 0),
    CONSTRAINT blog_agent_run_prompt_version_check CHECK (prompt_version > 0),
    CONSTRAINT blog_agent_run_status_check CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT blog_agent_run_token_check CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
    )
);

CREATE UNIQUE INDEX blog_agent_run_once_per_revision_idx
    ON blog_agent_run (post_id, post_revision, agent_id)
    WHERE status IN ('RUNNING', 'SUCCEEDED');

CREATE INDEX blog_agent_run_recent_idx
    ON blog_agent_run (started_at DESC, id);
