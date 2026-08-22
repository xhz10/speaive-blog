package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public final class V2__Add_post_visibility_and_media_access extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    ALTER TABLE blog_post
                    ADD COLUMN visibility VARCHAR(20)
                    """);
            statement.execute("UPDATE blog_post SET visibility = 'PUBLIC'");
            statement.execute("""
                    ALTER TABLE blog_post
                    ALTER COLUMN visibility SET NOT NULL,
                    ALTER COLUMN visibility SET DEFAULT 'ADMIN_ONLY',
                    ADD CONSTRAINT blog_post_visibility_check
                        CHECK (visibility IN ('PUBLIC', 'ADMIN_ONLY'))
                    """);

            statement.execute("""
                    ALTER TABLE blog_post_revision
                    ADD COLUMN visibility VARCHAR(20)
                    """);
            statement.execute("UPDATE blog_post_revision SET visibility = 'PUBLIC'");
            statement.execute("""
                    ALTER TABLE blog_post_revision
                    ALTER COLUMN visibility SET NOT NULL,
                    ALTER COLUMN visibility SET DEFAULT 'ADMIN_ONLY',
                    ADD CONSTRAINT blog_post_revision_visibility_check
                        CHECK (visibility IN ('PUBLIC', 'ADMIN_ONLY'))
                    """);

            statement.execute("""
                    CREATE TABLE blog_post_media (
                        post_id VARCHAR(36) NOT NULL,
                        relative_path VARCHAR(512) NOT NULL,
                        PRIMARY KEY (post_id, relative_path),
                        CONSTRAINT blog_post_media_post_fk FOREIGN KEY (post_id)
                            REFERENCES blog_post (id) ON DELETE CASCADE,
                        CONSTRAINT blog_post_media_media_fk FOREIGN KEY (relative_path)
                            REFERENCES blog_media (relative_path) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE INDEX blog_post_media_public_lookup_idx
                    ON blog_post_media (relative_path, post_id)
                    """);

            statement.execute("""
                    INSERT INTO blog_post_media (post_id, relative_path)
                    SELECT DISTINCT post.id, media.relative_path
                    FROM blog_post post
                    CROSS JOIN LATERAL regexp_matches(
                        post.body,
                        '/media/([A-Za-z0-9/_-]+[.](avif|gif|jpe?g|png|webp))',
                        'gi'
                    ) AS matched
                    JOIN blog_media media ON media.relative_path = matched[1]
                    ON CONFLICT DO NOTHING
                    """);
            statement.execute("""
                    INSERT INTO blog_post_media (post_id, relative_path)
                    SELECT post.id, media.relative_path
                    FROM blog_post post
                    JOIN blog_media media ON post.cover = '/media/' || media.relative_path
                    ON CONFLICT DO NOTHING
                    """);
        }
    }
}
