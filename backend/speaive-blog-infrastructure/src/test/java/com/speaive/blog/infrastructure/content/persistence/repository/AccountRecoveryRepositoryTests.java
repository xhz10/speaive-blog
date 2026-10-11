package com.speaive.blog.infrastructure.content.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.speaive.blog.application.command.account.ChangePasswordCommand;
import com.speaive.blog.application.command.account.CreateInvitationCommand;
import com.speaive.blog.application.command.account.RegisterMemberCommand;
import com.speaive.blog.application.command.account.ResetPasswordCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.AccountRepository;
import com.speaive.blog.application.port.out.persistence.InvitationRepository;
import com.speaive.blog.application.port.out.persistence.PasswordResetRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.service.MemberAccountApplicationService;
import com.speaive.blog.application.service.PasswordManagementApplicationService;
import com.speaive.blog.domain.account.MemberAccount;
import com.speaive.blog.domain.account.MemberRole;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAccountDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogInvitationDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.PasswordResetDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.AccountRecoveryPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.MemberPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.transaction.SpringTransactionRunner;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真实 PostgreSQL 和实际 MyBatis 适配器验证账号锁、一次性消费和多表事务。 */
@Testcontainers
class AccountRecoveryRepositoryTests {
    private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");
    private static DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private TransactionRunner transactions;
    private AccountRepository accounts;
    private InvitationRepository invitations;
    private PasswordResetRepository resets;
    private PasswordManagementApplicationService passwords;
    private MemberAccountApplicationService members;

    @BeforeAll
    static void installEmptyDatabase() {
        source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
    }

    @BeforeEach
    void prepareActualRepositories() throws Exception {
        jdbc = new JdbcTemplate(source);
        jdbc.update("DELETE FROM blog_user WHERE id IN (SELECT id FROM blog_account)");
        jdbc.update("DELETE FROM blog_invitation");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(BlogAccountDatabaseMapper.class, BlogAuthorDatabaseMapper.class,
                BlogInvitationDatabaseMapper.class, PasswordResetDatabaseMapper.class)) {
            configuration.addMapper(mapper);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject());
        var recoveryMapping = Mappers.getMapper(AccountRecoveryPersistenceMapStructMapper.class);
        accounts = new PostgresAccountRepository(sessions.getMapper(BlogAccountDatabaseMapper.class),
                sessions.getMapper(BlogAuthorDatabaseMapper.class), Mappers.getMapper(BlogAiPersistenceMapStructMapper.class),
                Mappers.getMapper(MemberPersistenceMapStructMapper.class));
        invitations = new PostgresInvitationRepository(sessions.getMapper(BlogInvitationDatabaseMapper.class), recoveryMapping);
        resets = new PostgresPasswordResetRepository(sessions.getMapper(PasswordResetDatabaseMapper.class), recoveryMapping);
        transactions = new SpringTransactionRunner(new DataSourceTransactionManager(source));
        passwords = passwordService(accounts);
        members = memberService(invitations);
    }

    @Test
    void issuingAndReissuingPersistOnlyHashesAndKeepExactlyOneActiveCredential() throws Exception {
        addProtectedWriter();
        var first = passwords.issueReset(" ALICE ");
        var second = passwords.issueReset("alice");
        assertThat(first.token()).hasSize(43).isNotEqualTo(second.token());
        assertThat(second.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(jdbc.queryForList("SELECT token_hash FROM blog_password_reset", String.class))
                .containsExactlyInAnyOrder(hash(first.token()), hash(second.token()))
                .doesNotContain(first.token(), second.token());
        assertThat(activeResets()).isEqualTo(1);
        assertThatThrownBy(() -> passwords.resetPassword(new ResetPasswordCommand(first.token(), "rejected-hash")))
                .isInstanceOfSatisfying(BlogException.class, error -> assertThat(error.code()).isEqualTo(BlogErrorCode.INVALID_REQUEST));
        assertThat(passwords.credentialsCurrent("alice", 1)).isTrue();
        assertWriterUnchanged("old-hash", 1);
    }

    @Test
    void concurrentConsumptionOfOneTokenChangesPasswordExactlyOnce() throws Exception {
        addProtectedWriter();
        var issued = passwords.issueReset("alice");
        var outcomes = race(() -> passwords.resetPassword(new ResetPasswordCommand(issued.token(), "left-hash")),
                () -> passwords.resetPassword(new ResetPasswordCommand(issued.token(), "right-hash")));
        assertThat(outcomes).containsExactlyInAnyOrder("success", "INVALID_REQUEST");
        assertThat(account().passwordHash()).isIn("left-hash", "right-hash");
        assertWriterUnchanged(account().passwordHash(), 2);
        assertThat(activeResets()).isZero();
        assertThat(passwords.credentialsCurrent("alice", 1)).isFalse();
        assertThat(passwords.credentialsCurrent("alice", 2)).isTrue();
    }

    @Test
    void passwordChangeAndTokenConsumptionSerializeOnTheSameAccount() throws Exception {
        addProtectedWriter();
        var issued = passwords.issueReset("alice");
        var outcomes = race(() -> passwords.changePassword("alice", new ChangePasswordCommand(1, "change-hash")),
                () -> passwords.resetPassword(new ResetPasswordCommand(issued.token(), "reset-hash")));
        assertThat(outcomes).isSubsetOf("success", "INVALID_REQUEST", "VERSION_CONFLICT");
        assertThat(outcomes.stream().filter("success"::equals)).hasSize(1);
        assertThat(account().passwordHash()).isIn("change-hash", "reset-hash");
        assertWriterUnchanged(account().passwordHash(), 2);
        assertThat(activeResets()).isZero();
    }

    @Test
    void reissueWaitsForAnInProgressPasswordResetAndUsesItsNewCredentialVersion() throws Exception {
        addProtectedWriter();
        var issued = passwords.issueReset("alice");
        var savedPassword = new CountDownLatch(1);
        var releaseReset = new CountDownLatch(1);
        var issuing = new CountDownLatch(1);
        var heldAccounts = intercept(AccountRepository.class, accounts, "savePassword", savedPassword, releaseReset, true);
        var observedAccounts = intercept(AccountRepository.class, accounts, "lockByUsername", issuing, null, false);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reset = executor.submit(() -> passwordService(heldAccounts).resetPassword(new ResetPasswordCommand(issued.token(), "new-hash")));
            try {
                await(savedPassword);
                var reissue = executor.submit(() -> passwordService(observedAccounts).issueReset("alice"));
                await(issuing);
                awaitDatabaseLock("blog_account");
                releaseReset.countDown();
                reset.get(10, TimeUnit.SECONDS);
                var next = reissue.get(10, TimeUnit.SECONDS);
                assertThat(resets.findByTokenHash(hash(next.token())).orElseThrow().credentialVersion()).isEqualTo(2);
                assertThat(activeResets()).isEqualTo(1);
                passwords.resetPassword(new ResetPasswordCommand(next.token(), "latest-hash"));
                assertWriterUnchanged("latest-hash", 3);
            } finally { releaseReset.countDown(); }
        }
    }

    @Test
    void consumptionAndRevocationFailuresRollBackPasswordAndKeepTokenUsable() {
        addProtectedWriter();
        var issued = passwords.issueReset("alice");
        jdbc.execute("ALTER TABLE blog_password_reset ADD CONSTRAINT test_no_consume CHECK (consumed_at IS NULL)");
        try {
            assertThatThrownBy(() -> passwords.resetPassword(new ResetPasswordCommand(issued.token(), "failed-reset")))
                    .isInstanceOf(RuntimeException.class);
            assertWriterUnchanged("old-hash", 1);
            assertThatThrownBy(() -> passwords.changePassword("alice", new ChangePasswordCommand(1, "failed-change")))
                    .isInstanceOf(RuntimeException.class);
            assertWriterUnchanged("old-hash", 1);
            assertThat(activeResets()).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE blog_password_reset DROP CONSTRAINT test_no_consume"); }
        passwords.resetPassword(new ResetPasswordCommand(issued.token(), "recovered-hash"));
        assertWriterUnchanged("recovered-hash", 2);
    }

    @Test
    void failedReissueDoesNotRevokeTheExistingToken() throws Exception {
        addProtectedWriter();
        var issued = passwords.issueReset("alice");
        jdbc.execute("ALTER TABLE blog_password_reset ADD CONSTRAINT test_no_reissue CHECK (token_hash = '" + hash(issued.token()) + "')");
        try {
            assertThatThrownBy(() -> passwords.issueReset("alice")).isInstanceOf(RuntimeException.class);
            assertThat(activeResets()).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE blog_password_reset DROP CONSTRAINT test_no_reissue"); }
        passwords.resetPassword(new ResetPasswordCommand(issued.token(), "recovered-hash"));
        assertWriterUnchanged("recovered-hash", 2);
    }

    @Test
    void registrationAndRevocationShareTheInvitationLockInEitherOrder() throws Exception {
        for (boolean revokeFirst : List.of(true, false)) {
            String username = revokeFirst ? "revoked" : "registered";
            var invite = members.createInvitation(new CreateInvitationCommand(7, 2, "  朋友邀请  "));
            var acquired = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var waiting = new CountDownLatch(1);
            String firstMethod = revokeFirst ? "lockById" : "lockByCodeHash";
            String secondMethod = revokeFirst ? "lockByCodeHash" : "lockById";
            var held = memberService(intercept(InvitationRepository.class, invitations, firstMethod, acquired, release, true));
            var observed = memberService(intercept(InvitationRepository.class, invitations, secondMethod, waiting, null, false));
            var registration = new RegisterMemberCommand(username, "朋友", "member-hash", invite.code());
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> outcome(() -> {
                    if (revokeFirst) held.revokeInvitation(invite.id()); else held.register(registration);
                }));
                try {
                    await(acquired);
                    var second = executor.submit(() -> outcome(() -> {
                        if (revokeFirst) observed.register(registration); else observed.revokeInvitation(invite.id());
                    }));
                    await(waiting);
                    awaitDatabaseLock("blog_invitation");
                    release.countDown();
                    assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("success");
                    assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(revokeFirst ? "INVALID_REQUEST" : "success");
                } finally { release.countDown(); }
            }
            var saved = members.listInvitations().items().stream().filter(value -> value.id().equals(invite.id())).findFirst().orElseThrow();
            assertThat(saved.note()).isEqualTo("朋友邀请");
            assertThat(saved.revokedAt()).isEqualTo(NOW);
            assertThat(saved.usedCount()).isEqualTo(revokeFirst ? 0 : 1);
            assertThat(accounts.findByUsername(username).isPresent()).isEqualTo(!revokeFirst);
            assertThat(members.revokeInvitation(invite.id())).isEqualTo(saved);
            assertThatThrownBy(() -> members.register(new RegisterMemberCommand(username + "again", "朋友", "member-hash", invite.code())))
                    .isInstanceOfSatisfying(BlogException.class, error -> assertThat(error.code()).isEqualTo(BlogErrorCode.INVALID_REQUEST));
        }
    }

    @Test
    void failedInvitationConsumptionRollsBackBothNewAccountAndItsIdentity() {
        var invite = members.createInvitation(new CreateInvitationCommand(7, 1, "仅测试事务"));
        jdbc.execute("ALTER TABLE blog_invitation ADD CONSTRAINT test_no_usage CHECK (used_count = 0)");
        try {
            assertThatThrownBy(() -> members.register(new RegisterMemberCommand("rollback", "朋友", "member-hash", invite.code())))
                    .isInstanceOf(RuntimeException.class);
            assertThat(accounts.findByUsername("rollback")).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_user WHERE username = 'rollback'", Integer.class)).isZero();
            assertThat(members.listInvitations().items().getFirst().usedCount()).isZero();
        } finally { jdbc.execute("ALTER TABLE blog_invitation DROP CONSTRAINT test_no_usage"); }
        assertThat(members.register(new RegisterMemberCommand("rollback", "朋友", "member-hash", invite.code())).username()).isEqualTo("rollback");
    }

    @Test
    void stalePasswordCasNeverOverwritesTheCurrentCredential() {
        addProtectedWriter();
        var original = account();
        passwords.changePassword("alice", new ChangePasswordCommand(1, "current-hash"));
        assertThatThrownBy(() -> transactions.required(() -> {
            accounts.savePassword(original.changePassword("stale-hash", 1, NOW), 1);
            return null;
        })).isInstanceOfSatisfying(BlogException.class, error -> assertThat(error.code()).isEqualTo(BlogErrorCode.VERSION_CONFLICT));
        assertWriterUnchanged("current-hash", 2);
    }

    private void addProtectedWriter() {
        var writer = MemberAccount.register(UUID.randomUUID().toString(), "alice", "原有作者", "old-hash", NOW)
                .changeWritingPermissions(MemberRole.WRITER, true, true, 1, NOW).chooseContentEncryption(true, 2, NOW);
        transactions.required(() -> { accounts.add(writer); return null; });
        jdbc.update("INSERT INTO blog_member_post_revision (id,owner_id,slug,status,visibility,created_at,updated_at,revision,archived,payload,payload_encrypted,event_type) VALUES ('archived',?,'old-private','DRAFT','ADMIN_ONLY',?,?,8,true,'unchanged-encrypted-payload',true,'ARCHIVE')",
                writer.id(), java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void assertWriterUnchanged(String hash, long credentialVersion) {
        var value = account();
        assertThat(value.passwordHash()).isEqualTo(hash);
        assertThat(value.credentialVersion()).isEqualTo(credentialVersion);
        assertThat(value.settingsVersion()).isEqualTo(3);
        assertThat(value.role()).isEqualTo(MemberRole.WRITER);
        assertThat(value.canPublish()).isTrue();
        assertThat(value.encryptionAllowed()).isTrue();
        assertThat(value.contentEncrypted()).isTrue();
        assertThat(value.identity().displayName()).isEqualTo("原有作者");
        assertThat(jdbc.queryForMap("SELECT payload,payload_encrypted,archived,revision FROM blog_member_post_revision WHERE owner_id = ?", value.id()))
                .containsEntry("payload", "unchanged-encrypted-payload").containsEntry("payload_encrypted", true)
                .containsEntry("archived", true).containsEntry("revision", 8L);
    }

    private MemberAccount account() { return accounts.findByUsername("alice").orElseThrow(); }
    private int activeResets() { return jdbc.queryForObject("SELECT count(*) FROM blog_password_reset WHERE consumed_at IS NULL", Integer.class); }
    private void awaitDatabaseLock(String table) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            int waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE ?
                    """, Integer.class, "%" + table + "%");
            if (waiting > 0) return;
            TimeUnit.MILLISECONDS.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Concurrent request did not wait on the PostgreSQL " + table + " lock");
    }
    private PasswordManagementApplicationService passwordService(AccountRepository repository) {
        return new PasswordManagementApplicationService(repository, resets, transactions, CLOCK);
    }
    private MemberAccountApplicationService memberService(InvitationRepository repository) {
        return new MemberAccountApplicationService(accounts, repository, transactions, CLOCK);
    }
    private static String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
    private static List<String> race(Runnable left, Runnable right) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<String> first = () -> { await(start); return outcome(left); };
            Callable<String> second = () -> { await(start); return outcome(right); };
            var firstResult = executor.submit(first);
            var secondResult = executor.submit(second);
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        }
    }
    private static String outcome(Runnable action) {
        try { action.run(); return "success"; }
        catch (BlogException error) { return error.code().name(); }
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("concurrent operation reached its lock boundary").isTrue();
    }
    private static <T> T intercept(Class<T> type, T delegate, String methodName, CountDownLatch reached,
            CountDownLatch release, boolean after) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            boolean intercept = method.getName().equals(methodName);
            if (intercept && !after) { reached.countDown(); if (release != null) await(release); }
            Object value;
            try { value = method.invoke(delegate, args); }
            catch (InvocationTargetException exception) { throw exception.getCause(); }
            if (intercept && after) { reached.countDown(); if (release != null) await(release); }
            return value;
        }));
    }
}
