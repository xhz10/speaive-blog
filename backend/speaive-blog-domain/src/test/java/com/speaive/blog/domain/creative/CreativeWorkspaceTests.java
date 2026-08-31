package com.speaive.blog.domain.creative;

import com.speaive.blog.domain.error.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeWorkspaceTests {
    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");

    @Test
    void inspirationTransitionsAreVersionedAndConvertedIdeasRequireATarget() {
        Inspiration idea = Inspiration.create(
                "idea-id", "雨里的站台", "她没有上车。", InspirationKind.SCENE, true, NOW);

        Inspiration developing = idea.transition(
                InspirationStatus.DEVELOPING, null, null, 1, NOW.plusSeconds(1));
        Inspiration converted = developing.transition(
                InspirationStatus.CONVERTED, CreativeContentType.NOVEL, "rainy-platform", 2, NOW.plusSeconds(2));

        assertEquals(3, converted.revision());
        assertEquals("rainy-platform", converted.targetSlug());
        assertThrows(DomainException.class, () -> idea.transition(
                InspirationStatus.CONVERTED, null, null, 1, NOW.plusSeconds(1)));
    }

    @Test
    void workCollectionsRejectDuplicateOrDiscontinuousItems() {
        List<WorkItem> duplicate = List.of(
                new WorkItem(CreativeContentType.POST, "one", 0),
                new WorkItem(CreativeContentType.POST, "one", 1));
        List<WorkItem> discontinuous = List.of(new WorkItem(CreativeContentType.POST, "one", 2));

        assertThrows(DomainException.class, () -> WorkCollection.create(
                "work-id", "work", "Work", "", null, CreativeVisibility.PUBLIC, duplicate, NOW));
        assertThrows(DomainException.class, () -> WorkCollection.create(
                "work-id", "work", "Work", "", null, CreativeVisibility.PUBLIC, discontinuous, NOW));
    }

    @Test
    void shareGrantExpiresAndCanBeRevokedWithoutExposingTheRawToken() {
        ShareGrant grant = ShareGrant.create(
                "share-id", CreativeContentType.POST, "secret", "a".repeat(64), NOW.plusSeconds(60), NOW);

        assertTrue(grant.activeAt(NOW.plusSeconds(30)));
        assertFalse(grant.activeAt(NOW.plusSeconds(61)));
        assertFalse(grant.revoke(NOW.plusSeconds(10)).activeAt(NOW.plusSeconds(20)));
    }
}
