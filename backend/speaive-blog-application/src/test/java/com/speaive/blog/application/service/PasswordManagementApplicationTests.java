package com.speaive.blog.application.service;

import com.speaive.blog.application.command.account.*;
import com.speaive.blog.application.error.*;
import com.speaive.blog.application.port.out.persistence.*;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.domain.account.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** 假端口只验证密码用例的锁顺序、领域行为与保密边界；实际并发由 PostgreSQL 集成测试覆盖。 */
class PasswordManagementApplicationTests {
    static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
    static final TransactionRunner TX = new TransactionRunner() {
        public <T> T required(Supplier<T> action) { return action.get(); }
    };

    @Test void issuingAndReissuingStoreOnlyHashAndDoNotChangePasswordOrSessionVersion() throws Exception {
        Fixture fixture = new Fixture();
        var first = fixture.service.issueReset(" ALICE ");
        assertEquals("alice", first.username());
        assertEquals(43, first.token().length());
        assertEquals(NOW.plusSeconds(1800), first.expiresAt());
        String firstHash = hash(first.token());
        assertTrue(fixture.resets.containsKey(firstHash));
        assertFalse(fixture.resets.containsKey(first.token()));
        assertEquals(1, fixture.account.credentialVersion());
        var second = fixture.service.issueReset("alice");
        assertNotEquals(first.token(), second.token());
        assertNotNull(fixture.resets.get(firstHash).consumedAt());
        assertTrue(fixture.service.credentialsCurrent("alice", 1));
        assertThrows(BlogException.class, () -> fixture.service.resetPassword(new ResetPasswordCommand(first.token(), "new-hash")));
        assertEquals("old-hash", fixture.account.passwordHash());
    }

    @Test void resetLocksAccountBeforeTokenAndAtomicallyAdvancesOnlyCredentialVersion() {
        Fixture fixture = new Fixture();
        fixture.account = fixture.account.changeWritingPermissions(MemberRole.WRITER, true, true, 1, NOW)
                .chooseContentEncryption(true, 2, NOW);
        var issued = fixture.service.issueReset("alice");
        fixture.calls.clear();
        fixture.service.resetPassword(new ResetPasswordCommand(issued.token(), "new-hash"));
        assertEquals(List.of("findByTokenHash", "lockById", "lockByTokenHash", "savePassword", "consume", "revokeForAccount"), fixture.calls);
        assertEquals(2, fixture.account.credentialVersion());
        assertEquals(3, fixture.account.settingsVersion());
        assertTrue(fixture.account.contentEncrypted());
        assertFalse(fixture.service.credentialsCurrent("alice", 1));
        assertTrue(fixture.service.credentialsCurrent("alice", 2));
        assertEquals(BlogErrorCode.INVALID_REQUEST, assertThrows(BlogException.class,
                () -> fixture.service.resetPassword(new ResetPasswordCommand(issued.token(), "another"))).code());
    }

    @Test void changeRejectsStaleVerificationAndRevokesAllExistingResetTokens() {
        Fixture fixture = new Fixture();
        var issued = fixture.service.issueReset("alice");
        fixture.service.changePassword("alice", new ChangePasswordCommand(1, "changed-hash"));
        assertEquals("changed-hash", fixture.account.passwordHash());
        assertEquals(1, fixture.account.settingsVersion());
        assertEquals(BlogErrorCode.VERSION_CONFLICT, assertThrows(BlogException.class,
                () -> fixture.service.changePassword("alice", new ChangePasswordCommand(1, "stale-hash"))).code());
        assertThrows(BlogException.class, () -> fixture.service.resetPassword(new ResetPasswordCommand(issued.token(), "reset-hash")));
        assertEquals("changed-hash", fixture.account.passwordHash());
    }

    @Test void expiryIsCheckedAfterWaitingForTheAccountLock() {
        Fixture fixture = new Fixture();
        var issued = fixture.service.issueReset("alice");
        fixture.advanceWhenLocked = true;
        assertEquals(BlogErrorCode.INVALID_REQUEST, assertThrows(BlogException.class,
                () -> fixture.service.resetPassword(new ResetPasswordCommand(issued.token(), "too-late"))).code());
        assertEquals("old-hash", fixture.account.passwordHash());
    }

    private static class Fixture {
        MemberAccount account = MemberAccount.register("member", "alice", "朋友", "old-hash", NOW);
        Map<String, PasswordReset> resets = new HashMap<>();
        List<String> calls = new ArrayList<>();
        Instant now = NOW;
        boolean advanceWhenLocked;
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now; }
        };
        AccountRepository accounts = fake(AccountRepository.class, (method, args) -> {
            calls.add(method);
            return switch (method) {
                case "findByUsername", "lockByUsername", "lockById" -> {
                    if (advanceWhenLocked && method.equals("lockById")) now = NOW.plusSeconds(1800);
                    yield Optional.of(account);
                }
                case "savePassword" -> { assertEquals(account.credentialVersion(), args[1]); account = (MemberAccount) args[0]; yield null; }
                default -> throw new AssertionError("密码用例不得覆盖其他设置: " + method);
            };
        });
        PasswordResetRepository repository = fake(PasswordResetRepository.class, (method, args) -> {
            calls.add(method);
            return switch (method) {
                case "findByTokenHash", "lockByTokenHash" -> Optional.ofNullable(resets.get(args[0]));
                case "add", "consume" -> { var value = (PasswordReset) args[0]; resets.put(value.tokenHash(), value); yield null; }
                case "revokeForAccount" -> {
                    resets.replaceAll((key, value) -> value.consumedAt() == null ? new PasswordReset(value.tokenHash(),
                            value.accountId(), value.credentialVersion(), value.expiresAt(), value.createdAt(), (Instant) args[1]) : value);
                    yield null;
                }
                default -> throw new AssertionError(method);
            };
        });
        PasswordManagementApplicationService service = new PasswordManagementApplicationService(accounts, repository, TX, clock);
    }
    private interface Call { Object run(String method, Object[] args); }
    private static <T> T fake(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> call.run(method.getName(), args == null ? new Object[0] : args)));
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
