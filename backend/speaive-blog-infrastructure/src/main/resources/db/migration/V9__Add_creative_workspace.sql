ALTER TABLE blog_post_revision DROP CONSTRAINT blog_post_revision_event_check;
ALTER TABLE blog_post_revision ADD CONSTRAINT blog_post_revision_event_check CHECK (
    event_type IN ('CREATE', 'IMPORT', 'UPDATE', 'PUBLISH', 'UNPUBLISH', 'ARCHIVE', 'RESTORE')
);

ALTER TABLE blog_novel_fragment_revision DROP CONSTRAINT blog_novel_fragment_revision_event_check;
ALTER TABLE blog_novel_fragment_revision ADD CONSTRAINT blog_novel_fragment_revision_event_check CHECK (
    event_type IN ('CREATE', 'UPDATE', 'PUBLISH', 'UNPUBLISH', 'RESTORE')
);

CREATE TABLE blog_inspiration (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INBOX',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    target_type VARCHAR(20),
    target_slug VARCHAR(100),
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_inspiration_title_length CHECK (char_length(title) BETWEEN 1 AND 120),
    CONSTRAINT blog_inspiration_body_length CHECK (char_length(body) BETWEEN 1 AND 10000),
    CONSTRAINT blog_inspiration_kind_check CHECK (kind IN ('IDEA', 'SCENE', 'DIALOGUE', 'CHARACTER', 'QUESTION')),
    CONSTRAINT blog_inspiration_status_check CHECK (status IN ('INBOX', 'DEVELOPING', 'CONVERTED', 'ARCHIVED')),
    CONSTRAINT blog_inspiration_target_type_check CHECK (target_type IS NULL OR target_type IN ('POST', 'NOVEL')),
    CONSTRAINT blog_inspiration_target_pair_check CHECK ((target_type IS NULL) = (target_slug IS NULL)),
    CONSTRAINT blog_inspiration_revision_check CHECK (revision >= 1),
    CONSTRAINT blog_inspiration_updated_check CHECK (updated_at >= created_at)
);

CREATE INDEX blog_inspiration_studio_order_idx
    ON blog_inspiration (pinned DESC, updated_at DESC, id);

CREATE TABLE blog_work_collection (
    id VARCHAR(36) PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(800) NOT NULL DEFAULT '',
    cover VARCHAR(2048),
    visibility VARCHAR(20) NOT NULL DEFAULT 'ADMIN_ONLY',
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_work_collection_slug_length CHECK (char_length(slug) BETWEEN 1 AND 100),
    CONSTRAINT blog_work_collection_title_length CHECK (char_length(title) BETWEEN 1 AND 200),
    CONSTRAINT blog_work_collection_description_length CHECK (char_length(description) <= 800),
    CONSTRAINT blog_work_collection_visibility_check CHECK (visibility IN ('PUBLIC', 'ADMIN_ONLY')),
    CONSTRAINT blog_work_collection_revision_check CHECK (revision >= 1),
    CONSTRAINT blog_work_collection_updated_check CHECK (updated_at >= created_at)
);

CREATE INDEX blog_work_collection_public_order_idx
    ON blog_work_collection (updated_at DESC, slug) WHERE visibility = 'PUBLIC';

CREATE TABLE blog_work_collection_item (
    collection_id VARCHAR(36) NOT NULL REFERENCES blog_work_collection(id) ON DELETE CASCADE,
    item_order INTEGER NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    content_slug VARCHAR(100) NOT NULL,
    PRIMARY KEY (collection_id, item_order),
    CONSTRAINT blog_work_collection_item_unique UNIQUE (collection_id, content_type, content_slug),
    CONSTRAINT blog_work_collection_item_type_check CHECK (content_type IN ('POST', 'NOVEL')),
    CONSTRAINT blog_work_collection_item_order_check CHECK (item_order >= 0)
);

CREATE INDEX blog_work_collection_item_lookup_idx
    ON blog_work_collection_item (content_type, content_slug, collection_id);

CREATE TABLE blog_share_grant (
    id VARCHAR(36) PRIMARY KEY,
    content_type VARCHAR(20) NOT NULL,
    content_slug VARCHAR(100) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_share_grant_type_check CHECK (content_type IN ('POST', 'NOVEL')),
    CONSTRAINT blog_share_grant_expiry_check CHECK (expires_at > created_at),
    CONSTRAINT blog_share_grant_revoke_check CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE INDEX blog_share_grant_content_idx
    ON blog_share_grant (content_type, content_slug, created_at DESC);

CREATE TABLE blog_editorial_review (
    id VARCHAR(36) PRIMARY KEY,
    post_id VARCHAR(36) NOT NULL REFERENCES blog_post(id) ON DELETE CASCADE,
    post_revision BIGINT NOT NULL,
    agent_id VARCHAR(36) NOT NULL REFERENCES blog_agent(id) ON DELETE RESTRICT,
    quote_text TEXT,
    quote_prefix VARCHAR(240),
    quote_suffix VARCHAR(240),
    body TEXT NOT NULL,
    model VARCHAR(200),
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_editorial_review_revision_check CHECK (post_revision >= 1),
    CONSTRAINT blog_editorial_review_quote_length CHECK (quote_text IS NULL OR char_length(quote_text) <= 4000),
    CONSTRAINT blog_editorial_review_body_length CHECK (char_length(body) BETWEEN 1 AND 8000),
    CONSTRAINT blog_editorial_review_token_check CHECK (
        (input_tokens IS NULL OR input_tokens >= 0) AND (output_tokens IS NULL OR output_tokens >= 0)
    )
);

CREATE INDEX blog_editorial_review_post_idx
    ON blog_editorial_review (post_id, created_at DESC);

CREATE TABLE blog_discussion_digest (
    post_id VARCHAR(36) PRIMARY KEY REFERENCES blog_post(id) ON DELETE CASCADE,
    post_revision BIGINT NOT NULL,
    comments_fingerprint CHAR(64) NOT NULL,
    body TEXT NOT NULL,
    model VARCHAR(200),
    input_tokens INTEGER,
    output_tokens INTEGER,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_discussion_digest_revision_check CHECK (post_revision >= 1),
    CONSTRAINT blog_discussion_digest_body_length CHECK (char_length(body) BETWEEN 1 AND 8000),
    CONSTRAINT blog_discussion_digest_token_check CHECK (
        (input_tokens IS NULL OR input_tokens >= 0) AND (output_tokens IS NULL OR output_tokens >= 0)
    )
);
