package com.speaive.blog.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DescriptionEntityMigrationTests {
    private static final String ACTIVE_POST_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ARCHIVED_POST_ID = "22222222-2222-2222-2222-222222222222";
    private static final String EXPLICIT_POST_ID = "33333333-3333-3333-3333-333333333333";
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String LEGACY_DESCRIPTION = "中文&#xff0c;emoji &#x1f600;";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("speaive_blog_description_migration_test")
            .withUsername("speaive")
            .withPassword("speaive-test-db-password");

    @Test
    void v3RepairsLegacyExtractorMatchesAndKeepsUnrelatedExplicitDescriptions() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load()
                .migrate();

        insertActivePost(jdbc);
        insertArchivedRevision(jdbc);
        insertExplicitPost(jdbc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForList("""
                SELECT description
                FROM blog_post
                ORDER BY id
                """, String.class)).containsExactly(
                "中文，emoji 😀",
                "保留 &#xff0c; 字面量"
        );
        assertThat(jdbc.queryForList("""
                SELECT description
                FROM blog_post_revision
                ORDER BY post_id, revision
                """, String.class)).containsExactly(
                "中文，emoji 😀",
                "中文，emoji 😀",
                "中文，emoji 😀",
                "😀".repeat(30),
                "保留 &#xff0c; 字面量"
        );
        assertThat(jdbc.queryForObject(
                "SELECT revision FROM blog_post WHERE id = ?", Long.class, ACTIVE_POST_ID)).isEqualTo(3L);
        assertThat(jdbc.queryForList("""
                SELECT event_type
                FROM blog_post_revision
                WHERE post_id = ?
                ORDER BY revision
                """, String.class, ACTIVE_POST_ID)).containsExactly("CREATE", "UPDATE", "UPDATE");
        assertThat(jdbc.queryForList("""
                SELECT tag
                FROM blog_post_revision_tag
                WHERE post_id = ? AND revision = 3
                ORDER BY tag_order
                """, String.class, ACTIVE_POST_ID)).containsExactly("迁移测试");
    }

    private static void insertActivePost(JdbcTemplate jdbc) {
        String originalBody = "中文，emoji 😀";
        String editedBody = originalBody + "\n\n后来补充正文";
        jdbc.update("""
                INSERT INTO blog_post (
                    id, slug, title, description, published_at, updated_at, status,
                    body, cover, author_id, revision, created_at
                ) VALUES (?, 'legacy-active', '历史文章', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    'PUBLISHED', ?, NULL, ?, 2, CURRENT_TIMESTAMP)
                """, ACTIVE_POST_ID, LEGACY_DESCRIPTION, editedBody, ADMIN_ID);
        jdbc.update("""
                INSERT INTO blog_post_tag (post_id, tag_order, tag)
                VALUES (?, 0, '迁移测试')
                """, ACTIVE_POST_ID);
        insertRevision(jdbc, ACTIVE_POST_ID, 1, "legacy-active", LEGACY_DESCRIPTION,
                originalBody, "CREATE");
        insertRevision(jdbc, ACTIVE_POST_ID, 2, "legacy-active", LEGACY_DESCRIPTION,
                editedBody, "UPDATE");
    }

    private static void insertArchivedRevision(JdbcTemplate jdbc) {
        String encodedEmoji = "&#x1f600;";
        String truncatedLegacyDescription = encodedEmoji.repeat(19) + "&#x1f6...";
        insertRevision(jdbc, ARCHIVED_POST_ID, 3, "legacy-archived", truncatedLegacyDescription,
                "😀".repeat(30), "ARCHIVE");
    }

    private static void insertExplicitPost(JdbcTemplate jdbc) {
        String explicitDescription = "保留 &#xff0c; 字面量";
        String body = "显式摘要正文，";
        jdbc.update("""
                INSERT INTO blog_post (
                    id, slug, title, description, published_at, updated_at, status,
                    body, cover, author_id, revision, created_at
                ) VALUES (?, 'explicit-description', '显式摘要', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    'PUBLISHED', ?, NULL, ?, 1, CURRENT_TIMESTAMP)
                """, EXPLICIT_POST_ID, explicitDescription, body, ADMIN_ID);
        insertRevision(jdbc, EXPLICIT_POST_ID, 1, "explicit-description", explicitDescription,
                body, "CREATE");
    }

    private static void insertRevision(
            JdbcTemplate jdbc,
            String postId,
            long revision,
            String slug,
            String description,
            String body,
            String eventType
    ) {
        jdbc.update("""
                INSERT INTO blog_post_revision (
                    post_id, revision, slug, title, description, published_at, updated_at,
                    status, body, cover, post_created_at, event_type, recorded_at, author_id
                ) VALUES (?, ?, ?, '历史文章', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    'PUBLISHED', ?, NULL, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
                """, postId, revision, slug, description, body, eventType, ADMIN_ID);
    }
}
