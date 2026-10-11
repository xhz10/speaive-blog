package com.speaive.blog.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

/** V12 真实旧库升级；换密版本回填不改密码、作者身份、加密正文或历史修订。 */
@Testcontainers
class AccountRecoveryMigrationTests {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");
    @Test void upgradesExistingAccountsInvitationsAndEncryptedArchivedHistoryWithoutRewritingData() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("12").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        String id = "11111111-1111-1111-1111-111111111111";
        jdbc.update("INSERT INTO blog_user (id,username,display_name,type,status,created_at,updated_at) VALUES (?,'legacy','原有作者','HUMAN','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
        jdbc.update("INSERT INTO blog_account (id,password_hash,role,can_publish,encryption_allowed,content_encrypted,settings_version,created_at,updated_at) VALUES (?,'existing-hash','WRITER',true,true,true,7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id);
        jdbc.update("INSERT INTO blog_invitation (id,code_hash,max_uses,used_count,expires_at,created_at,updated_at) VALUES ('22222222-2222-2222-2222-222222222222',?,3,2,CURRENT_TIMESTAMP + INTERVAL '1 day',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "a".repeat(64));
        jdbc.update("INSERT INTO blog_member_post_revision (id,owner_id,slug,status,visibility,created_at,updated_at,revision,archived,payload,payload_encrypted,event_type) VALUES ('archived',?,'old-private','DRAFT','ADMIN_ONLY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,8,true,'unchanged-encrypted-payload',true,'ARCHIVE')", id);
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForMap("SELECT role,can_publish,encryption_allowed,content_encrypted,settings_version,credential_version,password_hash FROM blog_account WHERE id = ?", id))
                .containsEntry("role", "WRITER").containsEntry("can_publish", true).containsEntry("encryption_allowed", true)
                .containsEntry("content_encrypted", true).containsEntry("settings_version", 7L)
                .containsEntry("credential_version", 1L).containsEntry("password_hash", "existing-hash");
        assertThat(jdbc.queryForMap("SELECT used_count,note,revoked_at FROM blog_invitation"))
                .containsEntry("used_count", 2).containsEntry("note", null).containsEntry("revoked_at", null);
        assertThat(jdbc.queryForMap("SELECT payload,payload_encrypted,archived,revision FROM blog_member_post_revision"))
                .containsEntry("payload", "unchanged-encrypted-payload").containsEntry("payload_encrypted", true)
                .containsEntry("archived", true).containsEntry("revision", 8L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_password_reset", Integer.class)).isZero();
    }
}
