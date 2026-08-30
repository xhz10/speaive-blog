CREATE TABLE blog_novel_fragment (
    id VARCHAR(36) PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    excerpt TEXT NOT NULL DEFAULT '',
    body TEXT NOT NULL DEFAULT '',
    author_id VARCHAR(36) NOT NULL REFERENCES blog_user(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    visibility TEXT NOT NULL DEFAULT 'ADMIN_ONLY',
    published_at TIMESTAMPTZ,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_novel_fragment_slug_length CHECK (char_length(slug) BETWEEN 1 AND 100),
    CONSTRAINT blog_novel_fragment_title_length CHECK (char_length(title) BETWEEN 1 AND 200),
    CONSTRAINT blog_novel_fragment_excerpt_length CHECK (char_length(excerpt) <= 500),
    CONSTRAINT blog_novel_fragment_status_check CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT blog_novel_fragment_visibility_check CHECK (visibility IN ('PUBLIC', 'ADMIN_ONLY')),
    CONSTRAINT blog_novel_fragment_revision_check CHECK (revision >= 1),
    CONSTRAINT blog_novel_fragment_published_time_check CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CONSTRAINT blog_novel_fragment_updated_time_check CHECK (updated_at >= created_at)
);

CREATE INDEX blog_novel_fragment_public_order_idx
    ON blog_novel_fragment (published_at DESC, slug)
    WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC';

CREATE INDEX blog_novel_fragment_studio_order_idx
    ON blog_novel_fragment (updated_at DESC, slug);

CREATE TABLE blog_novel_fragment_revision (
    fragment_id VARCHAR(36) NOT NULL,
    revision BIGINT NOT NULL,
    slug TEXT NOT NULL,
    title TEXT NOT NULL,
    excerpt TEXT NOT NULL,
    body TEXT NOT NULL,
    author_id VARCHAR(36) NOT NULL REFERENCES blog_user(id) ON DELETE RESTRICT,
    status TEXT NOT NULL,
    visibility TEXT NOT NULL,
    published_at TIMESTAMPTZ,
    fragment_created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    event_type TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (fragment_id, revision),
    CONSTRAINT blog_novel_fragment_revision_number_check CHECK (revision >= 1),
    CONSTRAINT blog_novel_fragment_revision_status_check CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT blog_novel_fragment_revision_visibility_check CHECK (visibility IN ('PUBLIC', 'ADMIN_ONLY')),
    CONSTRAINT blog_novel_fragment_revision_event_check CHECK (
        event_type IN ('CREATE', 'UPDATE', 'PUBLISH', 'UNPUBLISH')
    )
);

CREATE INDEX blog_novel_fragment_revision_recorded_idx
    ON blog_novel_fragment_revision (fragment_id, recorded_at DESC);
