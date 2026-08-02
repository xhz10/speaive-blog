package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public final class V1__Create_blog_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("""
                    CREATE TABLE blog_post (
                        id VARCHAR(36) PRIMARY KEY,
                        slug VARCHAR(100) NOT NULL UNIQUE,
                        title VARCHAR(200) NOT NULL,
                        description VARCHAR(500) NOT NULL,
                        published_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        body TEXT NOT NULL,
                        cover VARCHAR(2048),
                        revision BIGINT NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT blog_post_status_check CHECK (status IN ('DRAFT', 'PUBLISHED')),
                        CONSTRAINT blog_post_revision_check CHECK (revision > 0)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX blog_post_public_order_idx
                    ON blog_post (status, published_at DESC, slug)
                    """);
            statement.execute("""
                    CREATE TABLE blog_post_tag (
                        post_id VARCHAR(36) NOT NULL,
                        tag_order INTEGER NOT NULL,
                        tag VARCHAR(40) NOT NULL,
                        PRIMARY KEY (post_id, tag_order),
                        CONSTRAINT blog_post_tag_unique UNIQUE (post_id, tag),
                        CONSTRAINT blog_post_tag_post_fk FOREIGN KEY (post_id)
                            REFERENCES blog_post (id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE blog_post_revision (
                        post_id VARCHAR(36) NOT NULL,
                        revision BIGINT NOT NULL,
                        slug VARCHAR(100) NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        description VARCHAR(500) NOT NULL,
                        published_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        body TEXT NOT NULL,
                        cover VARCHAR(2048),
                        post_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        event_type VARCHAR(20) NOT NULL,
                        recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        PRIMARY KEY (post_id, revision),
                        CONSTRAINT blog_post_revision_status_check CHECK (status IN ('DRAFT', 'PUBLISHED')),
                        CONSTRAINT blog_post_revision_event_check CHECK (
                            event_type IN ('CREATE', 'IMPORT', 'UPDATE', 'PUBLISH', 'UNPUBLISH', 'ARCHIVE')
                        )
                    )
                    """);
            statement.execute("""
                    CREATE INDEX blog_post_revision_slug_idx
                    ON blog_post_revision (slug, recorded_at DESC)
                    """);
            statement.execute("""
                    CREATE TABLE blog_post_revision_tag (
                        post_id VARCHAR(36) NOT NULL,
                        revision BIGINT NOT NULL,
                        tag_order INTEGER NOT NULL,
                        tag VARCHAR(40) NOT NULL,
                        PRIMARY KEY (post_id, revision, tag_order),
                        CONSTRAINT blog_post_revision_tag_unique UNIQUE (post_id, revision, tag),
                        CONSTRAINT blog_post_revision_tag_revision_fk FOREIGN KEY (post_id, revision)
                            REFERENCES blog_post_revision (post_id, revision) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE blog_media (
                        relative_path VARCHAR(512) PRIMARY KEY,
                        original_file_name VARCHAR(255) NOT NULL,
                        mime_type VARCHAR(100) NOT NULL,
                        size_bytes BIGINT NOT NULL,
                        sha256 CHAR(64) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT blog_media_size_check CHECK (size_bytes > 0)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX blog_media_sha256_idx ON blog_media (sha256)
                    """);
            statement.execute("""
                    CREATE TABLE blog_markdown_import (
                        sha256 CHAR(64) PRIMARY KEY,
                        original_file_name VARCHAR(255) NOT NULL,
                        slug VARCHAR(100) NOT NULL,
                        imported_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
        }
    }
}
