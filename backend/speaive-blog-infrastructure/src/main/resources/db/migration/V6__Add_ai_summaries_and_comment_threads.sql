CREATE TABLE blog_post_ai_summary (
    post_id VARCHAR(36) PRIMARY KEY,
    post_revision BIGINT NOT NULL,
    body TEXT NOT NULL,
    model VARCHAR(120),
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT blog_post_ai_summary_post_fk FOREIGN KEY (post_id)
        REFERENCES blog_post (id) ON DELETE CASCADE,
    CONSTRAINT blog_post_ai_summary_revision_check CHECK (post_revision > 0),
    CONSTRAINT blog_post_ai_summary_body_not_blank CHECK (BTRIM(body) <> ''),
    CONSTRAINT blog_post_ai_summary_body_length CHECK (CHAR_LENGTH(body) <= 1000),
    CONSTRAINT blog_post_ai_summary_model_not_blank CHECK (model IS NULL OR BTRIM(model) <> ''),
    CONSTRAINT blog_post_ai_summary_token_check CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
    )
);

ALTER TABLE blog_comment
    ADD COLUMN parent_comment_id VARCHAR(36),
    ADD CONSTRAINT blog_comment_parent_fk FOREIGN KEY (parent_comment_id)
        REFERENCES blog_comment (id) ON DELETE CASCADE,
    ADD CONSTRAINT blog_comment_not_self_parent CHECK (parent_comment_id IS NULL OR parent_comment_id <> id);

CREATE INDEX blog_comment_parent_order_idx
    ON blog_comment (parent_comment_id, created_at, id)
    WHERE parent_comment_id IS NOT NULL;

ALTER TABLE blog_agent_run
    ADD COLUMN target_comment_id VARCHAR(36),
    ADD CONSTRAINT blog_agent_run_target_comment_fk FOREIGN KEY (target_comment_id)
        REFERENCES blog_comment (id) ON DELETE SET NULL;

DROP INDEX blog_agent_run_once_per_revision_idx;

CREATE UNIQUE INDEX blog_agent_run_once_per_revision_idx
    ON blog_agent_run (post_id, post_revision, agent_id)
    WHERE target_comment_id IS NULL AND status IN ('RUNNING', 'SUCCEEDED');

CREATE UNIQUE INDEX blog_agent_run_once_per_reply_target_idx
    ON blog_agent_run (target_comment_id, agent_id)
    WHERE target_comment_id IS NOT NULL AND status IN ('RUNNING', 'SUCCEEDED');
