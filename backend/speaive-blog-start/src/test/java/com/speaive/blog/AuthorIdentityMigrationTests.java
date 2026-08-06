package com.speaive.blog;

import com.speaive.blog.domain.Author;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AuthorIdentityMigrationTests {
    private static final String ACTIVE_POST_ID = "11111111-1111-1111-1111-111111111111";
    private static final String ARCHIVED_POST_ID = "22222222-2222-2222-2222-222222222222";
    private static final String MIGRATION_ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String MIGRATION_ADMIN_USERNAME = "admin";
    private static final String MIGRATION_ADMIN_DISPLAY_NAME = "Speaive";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("speaive_blog_author_migration_test")
            .withUsername("speaive")
            .withPassword("speaive-test-db-password");

    @Test
    void v2BackfillsActivePostsAndRevisionOnlyArchiveHistoryBeforeAddingConstraints() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        jdbc.update("""
                INSERT INTO blog_post (
                    id, slug, title, description, published_at, updated_at, status,
                    body, cover, revision, created_at
                ) VALUES (?, 'legacy-active', '历史文章', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    'PUBLISHED', '正文', NULL, 1, CURRENT_TIMESTAMP)
                """, ACTIVE_POST_ID);
        jdbc.update("""
                INSERT INTO blog_post_revision (
                    post_id, revision, slug, title, description, published_at, updated_at,
                    status, body, cover, post_created_at, event_type, recorded_at
                ) VALUES (?, 1, 'legacy-active', '历史文章', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    'PUBLISHED', '正文', NULL, CURRENT_TIMESTAMP, 'CREATE', CURRENT_TIMESTAMP)
                """, ACTIVE_POST_ID);
        jdbc.update("""
                INSERT INTO blog_post_revision (
                    post_id, revision, slug, title, description, published_at, updated_at,
                    status, body, cover, post_created_at, event_type, recorded_at
                ) VALUES (?, 3, 'legacy-archived', '已归档文章', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    'DRAFT', '归档正文', NULL, CURRENT_TIMESTAMP, 'ARCHIVE', CURRENT_TIMESTAMP)
                """, ARCHIVED_POST_ID);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(Author.ADMIN_ID).isEqualTo(MIGRATION_ADMIN_ID);
        assertThat(Author.ADMIN.username()).isEqualTo(MIGRATION_ADMIN_USERNAME);
        assertThat(Author.ADMIN.displayName()).isEqualTo(MIGRATION_ADMIN_DISPLAY_NAME);
        assertThat(Author.ADMIN.type().name()).isEqualTo("HUMAN");
        assertThat(Author.ADMIN.avatarUrl()).isNull();
        assertThat(jdbc.queryForMap("SELECT * FROM blog_user WHERE id = ?", MIGRATION_ADMIN_ID))
                .containsEntry("username", MIGRATION_ADMIN_USERNAME)
                .containsEntry("display_name", MIGRATION_ADMIN_DISPLAY_NAME)
                .containsEntry("type", "HUMAN")
                .containsEntry("status", "ACTIVE")
                .containsEntry("avatar_url", null);
        assertThat(jdbc.queryForObject(
                "SELECT author_id FROM blog_post WHERE id = ?", String.class, ACTIVE_POST_ID))
                .isEqualTo(MIGRATION_ADMIN_ID);
        assertThat(jdbc.queryForList(
                "SELECT author_id FROM blog_post_revision WHERE post_id IN (?, ?) ORDER BY post_id",
                String.class, ACTIVE_POST_ID, ARCHIVED_POST_ID))
                .containsExactly(MIGRATION_ADMIN_ID, MIGRATION_ADMIN_ID);
        assertThat(jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND column_name = 'author_id'
                  AND is_nullable = 'NO'
                  AND table_name IN ('blog_post', 'blog_post_revision')
                ORDER BY table_name
                """, String.class)).containsExactly("blog_post", "blog_post_revision");
        assertThat(jdbc.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_type = 'FOREIGN KEY'
                  AND constraint_name IN ('blog_post_author_fk', 'blog_post_revision_author_fk')
                ORDER BY constraint_name
                """, String.class))
                .containsExactly("blog_post_author_fk", "blog_post_revision_author_fk");
        assertThat(jdbc.queryForList("""
                SELECT constraint_name
                FROM information_schema.referential_constraints
                WHERE constraint_schema = 'public'
                  AND delete_rule = 'RESTRICT'
                  AND constraint_name IN ('blog_post_author_fk', 'blog_post_revision_author_fk')
                ORDER BY constraint_name
                """, String.class))
                .containsExactly("blog_post_author_fk", "blog_post_revision_author_fk");
        assertThat(jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                      'blog_post_author_idx',
                      'blog_post_revision_author_idx',
                      'blog_user_username_normalized_idx'
                  )
                ORDER BY indexname
                """, String.class))
                .containsExactly(
                        "blog_post_author_idx",
                        "blog_post_revision_author_idx",
                        "blog_user_username_normalized_idx");
        assertThat(jdbc.queryForObject("""
                SELECT definition.indisunique
                FROM pg_class index_relation
                JOIN pg_index definition ON definition.indexrelid = index_relation.oid
                JOIN pg_class table_relation ON table_relation.oid = definition.indrelid
                WHERE table_relation.relname = 'blog_user'
                  AND index_relation.relname = 'blog_user_username_normalized_idx'
                """, Boolean.class)).isTrue();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO blog_user (
                    id, username, display_name, type, status, avatar_url, created_at, updated_at
                ) VALUES (?, ?, '大小写用户名', 'AGENT', 'ACTIVE', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "33333333-3333-3333-3333-333333333333", "Fresh-Agent"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO blog_user (
                    id, username, display_name, type, status, avatar_url, created_at, updated_at
                ) VALUES (?, ?, '尾随空格', 'AGENT', 'ACTIVE', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "44444444-4444-4444-4444-444444444444", "agent-name "))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO blog_user (
                    id, username, display_name, type, status, avatar_url, created_at, updated_at
                ) VALUES (?, ?, '重复管理员', 'AGENT', 'ACTIVE', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "55555555-5555-5555-5555-555555555555", "admin"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
