package com.speaive.blog.domain.account;

import com.speaive.blog.domain.error.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberAccountTests {
    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");

    @Test
    void invitationTracksExpiryAndUsageInsideTheDomain() {
        Invitation invitation = Invitation.create(
                "00000000-0000-0000-0000-000000000010", "a".repeat(64), 2,
                NOW.plusSeconds(3600), NOW);

        assertTrue(invitation.usableAt(NOW));
        Invitation once = invitation.consume(NOW.plusSeconds(1));
        Invitation twice = once.consume(NOW.plusSeconds(2));
        assertEquals(2, twice.usedCount());
        assertFalse(twice.usableAt(NOW.plusSeconds(3)));
        assertThrows(DomainException.class, () -> twice.consume(NOW.plusSeconds(3)));
    }

    @Test
    void registeredMemberIsAnEnabledHumanIdentityWithoutAdminPrivileges() {
        MemberAccount account = MemberAccount.register(
                "00000000-0000-0000-0000-000000000020", "reader", "读者", "$2a$12$" + "x".repeat(53), NOW);

        assertTrue(account.enabled());
        assertEquals("reader", account.identity().username());
    }
    @Test
    void writingPublishingAndEncryptionAreIndependentAndVersioned() {
        MemberAccount reader = MemberAccount.register("member", "reader", "读者", "hash", NOW);
        assertEquals(MemberRole.READER, reader.role());
        assertThrows(DomainException.class, reader::ensureCanWrite);
        assertThrows(DomainException.class, () -> reader.chooseContentEncryption(true, 1, NOW));
        assertThrows(DomainException.class, () -> reader.changeWritingPermissions(MemberRole.READER, true, true, 1, NOW));
        MemberAccount writer = reader.changeWritingPermissions(MemberRole.WRITER, false, true, 1, NOW);
        writer.ensureCanWrite();
        assertThrows(DomainException.class, writer::ensureCanPublish);
        MemberAccount encrypted = writer.chooseContentEncryption(true, 2, NOW);
        assertTrue(encrypted.contentEncrypted());
        assertThrows(DomainException.class, () -> encrypted.chooseContentEncryption(false, 2, NOW));
        assertThrows(DomainException.class, () -> encrypted.changeWritingPermissions(MemberRole.WRITER, true, false, 3, NOW));
        MemberAccount publisher = encrypted.changeWritingPermissions(MemberRole.WRITER, true, true, 3, NOW);
        publisher.ensureCanPublish();
        assertTrue(publisher.contentEncrypted());
        assertFalse(publisher.chooseContentEncryption(false, 4, NOW).contentEncrypted());
    }
}
