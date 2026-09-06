package com.speaive.blog.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** V10 真实账号升级，原始账号密码和站长文章保持原值，旧会员不会自动获得作者资格。 */
@Testcontainers
class MemberWritingMigrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");
    @Test void upgradesExistingAccountsWithoutGrantingPermissionsOrEncryptingAdminContent() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("10").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        String id = "11111111-1111-1111-1111-111111111111";
        jdbc.update("INSERT INTO blog_user (id, username, display_name, type, status, created_at, updated_at) VALUES (?, 'legacy', '原有会员', 'HUMAN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id);
        jdbc.update("INSERT INTO blog_account (id, password_hash, created_at, updated_at) VALUES (?, 'existing-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id);
        jdbc.update("INSERT INTO blog_post (id,slug,title,description,published_at,updated_at,status,body,author_id,revision,created_at) VALUES ('22222222-2222-2222-2222-222222222222','admin-legacy','站长原有标题','摘要',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'DRAFT','站长原有正文','00000000-0000-0000-0000-000000000001',1,CURRENT_TIMESTAMP)");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForMap("SELECT role, can_publish, encryption_allowed, content_encrypted, settings_version, password_hash FROM blog_account WHERE id = ?", id))
                .containsEntry("role", "READER").containsEntry("can_publish", false).containsEntry("encryption_allowed", false)
                .containsEntry("content_encrypted", false).containsEntry("settings_version", 1L).containsEntry("password_hash", "existing-hash");
        assertThat(jdbc.queryForMap("SELECT title, body FROM blog_post WHERE slug = 'admin-legacy'"))
                .containsEntry("title", "站长原有标题").containsEntry("body", "站长原有正文");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_member_post", Integer.class)).isZero();
    }
}
