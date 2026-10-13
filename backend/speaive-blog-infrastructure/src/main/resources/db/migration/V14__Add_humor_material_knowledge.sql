-- PostgreSQL keeps the source material; the Lucene index is a replaceable in-memory projection.
CREATE FUNCTION blog_humor_topics_valid(values_to_check TEXT[])
RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT CARDINALITY(values_to_check) BETWEEN 1 AND 12
        AND ARRAY_NDIMS(values_to_check) = 1
        AND NOT EXISTS (
            SELECT 1 FROM UNNEST(values_to_check) AS topic
            WHERE topic IS NULL OR BTRIM(topic) = '' OR CHAR_LENGTH(topic) > 50
        );
$$;

CREATE TABLE blog_humor_material (
    id VARCHAR(80) PRIMARY KEY,
    excerpt TEXT NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    source_title VARCHAR(200) NOT NULL,
    source_kind VARCHAR(20) NOT NULL,
    topics TEXT[] NOT NULL,
    mechanism VARCHAR(24) NOT NULL,
    analysis TEXT NOT NULL,
    adaptation TEXT NOT NULL,
    quality INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT blog_humor_material_id_check CHECK (BTRIM(id) <> ''),
    CONSTRAINT blog_humor_material_excerpt_check CHECK (
        BTRIM(excerpt) <> '' AND CHAR_LENGTH(excerpt) <= 500
    ),
    CONSTRAINT blog_humor_material_source_url_check CHECK (source_url ~* '^https?://[^[:space:]]+$'),
    CONSTRAINT blog_humor_material_source_title_check CHECK (BTRIM(source_title) <> ''),
    CONSTRAINT blog_humor_material_source_kind_check CHECK (
        source_kind IN ('PRIMARY_LISTING', 'PRIMARY_POST', 'REPRINT')
    ),
    CONSTRAINT blog_humor_material_topics_check CHECK (blog_humor_topics_valid(topics)),
    CONSTRAINT blog_humor_material_mechanism_check CHECK (
        mechanism IN ('LITERAL', 'CAUSAL_REVERSAL', 'CONCEPT_MISMATCH', 'DAILY_LIFE', 'PERSONIFICATION')
    ),
    CONSTRAINT blog_humor_material_analysis_check CHECK (
        BTRIM(analysis) <> '' AND CHAR_LENGTH(analysis) <= 1200
    ),
    CONSTRAINT blog_humor_material_adaptation_check CHECK (
        BTRIM(adaptation) <> '' AND CHAR_LENGTH(adaptation) <= 1200
    ),
    CONSTRAINT blog_humor_material_quality_check CHECK (quality BETWEEN 1 AND 5),
    CONSTRAINT blog_humor_material_version_check CHECK (version > 0),
    CONSTRAINT blog_humor_material_time_check CHECK (updated_at >= created_at)
);

CREATE INDEX blog_humor_material_enabled_mechanism_idx
    ON blog_humor_material (mechanism, id) WHERE enabled;

CREATE TABLE blog_agent_humor_profile (
    agent_id VARCHAR(36) PRIMARY KEY REFERENCES blog_agent (id) ON DELETE CASCADE,
    mechanism VARCHAR(24) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT blog_agent_humor_profile_mechanism_check CHECK (
        mechanism IN ('LITERAL', 'CAUSAL_REVERSAL', 'CONCEPT_MISMATCH', 'DAILY_LIFE', 'PERSONIFICATION')
    )
);

CREATE TABLE blog_agent_run_humor_material (
    run_id VARCHAR(36) NOT NULL REFERENCES blog_agent_run (id) ON DELETE CASCADE,
    material_id VARCHAR(80) NOT NULL REFERENCES blog_humor_material (id) ON DELETE RESTRICT,
    material_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, material_id),
    CONSTRAINT blog_agent_run_humor_material_order_unique UNIQUE (run_id, material_order),
    CONSTRAINT blog_agent_run_humor_material_order_check CHECK (material_order >= 0)
);

CREATE INDEX blog_agent_run_humor_material_recent_idx
    ON blog_agent_run_humor_material (created_at DESC, run_id, material_order);
