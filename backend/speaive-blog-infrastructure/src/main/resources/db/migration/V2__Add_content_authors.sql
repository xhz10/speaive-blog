CREATE TABLE blog_user (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    avatar_url VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT blog_user_username_canonical CHECK (
        username = LOWER(BTRIM(username)) AND username <> ''
    ),
    CONSTRAINT blog_user_display_name_not_blank CHECK (BTRIM(display_name) <> ''),
    CONSTRAINT blog_user_type_check CHECK (type IN ('HUMAN', 'AGENT', 'SYSTEM')),
    CONSTRAINT blog_user_status_check CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX blog_user_username_normalized_idx
    ON blog_user (LOWER(BTRIM(username)));

INSERT INTO blog_user (
    id, username, display_name, type, status, avatar_url, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    'Speaive',
    'HUMAN',
    'ACTIVE',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

ALTER TABLE blog_post ADD COLUMN author_id VARCHAR(36);
ALTER TABLE blog_post_revision ADD COLUMN author_id VARCHAR(36);

UPDATE blog_post
SET author_id = '00000000-0000-0000-0000-000000000001';

UPDATE blog_post_revision
SET author_id = '00000000-0000-0000-0000-000000000001';

ALTER TABLE blog_post ALTER COLUMN author_id SET NOT NULL;
ALTER TABLE blog_post_revision ALTER COLUMN author_id SET NOT NULL;

ALTER TABLE blog_post
    ADD CONSTRAINT blog_post_author_fk FOREIGN KEY (author_id)
        REFERENCES blog_user (id) ON DELETE RESTRICT;

ALTER TABLE blog_post_revision
    ADD CONSTRAINT blog_post_revision_author_fk FOREIGN KEY (author_id)
        REFERENCES blog_user (id) ON DELETE RESTRICT;

CREATE INDEX blog_post_author_idx ON blog_post (author_id);
CREATE INDEX blog_post_revision_author_idx ON blog_post_revision (author_id);
