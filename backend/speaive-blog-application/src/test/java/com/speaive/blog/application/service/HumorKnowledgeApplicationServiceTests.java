package com.speaive.blog.application.service;

import com.speaive.blog.application.command.knowledge.SaveHumorMaterialCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.knowledge.HumorSearchHit;
import com.speaive.blog.application.port.out.knowledge.HumorSearchQuery;
import com.speaive.blog.application.port.out.persistence.HumorMaterialRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.domain.knowledge.AgentHumorProfile;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import com.speaive.blog.domain.knowledge.HumorMechanism;
import com.speaive.blog.domain.knowledge.HumorSourceKind;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumorKnowledgeApplicationServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createAndListReturnVersionedMetadataThroughOneTransactionEach() {
        Fixture fixture = new Fixture();

        var created = fixture.service.createMaterial(command(true, 1));
        var listed = fixture.service.listMaterials();

        assertNotNull(created.id());
        assertFalse(created.id().isBlank());
        assertEquals("原句", created.excerpt());
        assertEquals("https://example.com/source", created.sourceUrl());
        assertEquals("PRIMARY_POST", created.sourceKind());
        assertEquals("LITERAL", created.mechanism());
        assertEquals(List.of("时间", "时钟"), created.topics());
        assertEquals(1, created.version());
        assertEquals(4, created.quality());
        assertTrue(created.enabled());
        assertEquals(NOW, created.createdAt());
        assertEquals(NOW, created.updatedAt());
        assertEquals(0, created.score());
        assertEquals(List.of(created), listed);
        assertEquals(2, fixture.transactions.calls);
        assertEquals(1, fixture.repository.adds);
    }

    @Test
    void updateChangesStateAndIncrementsVersionWithoutChangingIdentity() {
        Fixture fixture = new Fixture();
        var created = fixture.service.createMaterial(command(true, 1));

        var updated = fixture.service.updateMaterial(created.id(), command(false, 1));

        assertEquals(created.id(), updated.id());
        assertEquals(created.createdAt(), updated.createdAt());
        assertEquals(2, updated.version());
        assertFalse(updated.enabled());
        assertEquals(List.of(updated), fixture.service.listMaterials());
        assertEquals(1, fixture.repository.updates);
    }

    @Test
    void staleVersionAndUnknownIdentityCannotOverwriteExistingMaterial() {
        Fixture fixture = new Fixture();
        var created = fixture.service.createMaterial(command(true, 1));
        fixture.service.updateMaterial(created.id(), command(false, 1));

        assertCode(BlogErrorCode.VERSION_CONFLICT,
                () -> fixture.service.updateMaterial(created.id(), command(true, 1)));
        assertCode(BlogErrorCode.NOT_FOUND,
                () -> fixture.service.updateMaterial("missing", command(true, 1)));

        assertEquals(1, fixture.repository.updates);
        assertFalse(fixture.repository.values.get(created.id()).enabled());
        assertEquals(2, fixture.repository.values.get(created.id()).version());
    }

    @Test
    void repositoryCasConflictAfterReadIsPreservedAsVersionConflict() {
        Fixture fixture = new Fixture();
        var created = fixture.service.createMaterial(command(true, 1));
        fixture.repository.simulateConcurrentUpdate = true;

        assertCode(BlogErrorCode.VERSION_CONFLICT,
                () -> fixture.service.updateMaterial(created.id(), command(false, 1)));

        assertEquals(0, fixture.repository.updates);
        assertTrue(fixture.repository.values.get(created.id()).enabled());
    }

    @Test
    void invalidEnumsSourcesTopicsAndQualityBecomeInvalidRequestBeforeWrites() {
        Fixture fixture = new Fixture();
        for (SaveHumorMaterialCommand invalid : List.of(
                command("UNKNOWN", "LITERAL", List.of("时间"), "https://example.com/source", 4, true, 1),
                command("PRIMARY_POST", "UNKNOWN", List.of("时间"), "https://example.com/source", 4, true, 1),
                command(null, "LITERAL", List.of("时间"), "https://example.com/source", 4, true, 1),
                command("PRIMARY_POST", null, List.of("时间"), "https://example.com/source", 4, true, 1),
                command(" ", "LITERAL", List.of("时间"), "https://example.com/source", 4, true, 1),
                command("PRIMARY_POST", "LITERAL", List.of("时间"), "javascript:alert(1)", 4, true, 1),
                command("PRIMARY_POST", "LITERAL", List.of(), "https://example.com/source", 4, true, 1),
                command("PRIMARY_POST", "LITERAL", null, "https://example.com/source", 4, true, 1),
                command("PRIMARY_POST", "LITERAL", List.of("时间"), "https://example.com/source", 6, true, 1))) {
            assertCode(BlogErrorCode.INVALID_REQUEST, () -> fixture.service.createMaterial(invalid));
        }

        assertEquals(0, fixture.repository.adds);
        assertTrue(fixture.repository.values.isEmpty());
    }

    @Test
    void searchPreviewPassesFilterAndPreservesScoresWithoutWritingMaterial() {
        Fixture fixture = new Fixture();
        HumorMaterial material = sample("sample");
        fixture.hits = List.of(new HumorSearchHit(material, 2.75));

        var result = fixture.service.searchMaterials("  时间过期  ", "LITERAL");

        assertEquals(new HumorSearchQuery("时间过期", HumorMechanism.LITERAL, 20), fixture.queries.getFirst());
        assertEquals("sample", result.getFirst().id());
        assertEquals(2.75, result.getFirst().score());
        assertEquals("LITERAL", result.getFirst().mechanism());
        assertEquals(0, fixture.repository.adds);
        assertEquals(0, fixture.repository.updates);
        fixture.hits = List.of();
        assertTrue(fixture.service.searchMaterials("没有素材", null).isEmpty());
        assertEquals(null, fixture.queries.getLast().mechanism());
    }

    @Test
    void invalidSearchMechanismAndOversizedQueryDoNotReachSearchAdapter() {
        Fixture fixture = new Fixture();

        assertCode(BlogErrorCode.INVALID_REQUEST, () -> fixture.service.searchMaterials("时间", "UNKNOWN"));
        assertCode(BlogErrorCode.INVALID_REQUEST, () -> fixture.service.searchMaterials("长".repeat(1_001), null));

        assertTrue(fixture.queries.isEmpty());
    }

    private static SaveHumorMaterialCommand command(boolean enabled, long version) {
        return command("PRIMARY_POST", "LITERAL", List.of("时间", "时钟"),
                "https://example.com/source", 4, enabled, version);
    }

    private static SaveHumorMaterialCommand command(String sourceKind, String mechanism, List<String> topics,
            String sourceUrl, int quality, boolean enabled, long version) {
        return new SaveHumorMaterialCommand("原句", sourceUrl, "来源标题", sourceKind, topics,
                mechanism, "笑点分析", "原创示例", quality, enabled, version);
    }

    private static HumorMaterial sample(String id) {
        return HumorMaterial.create(id, "原句", "https://example.com/source", "来源标题",
                HumorSourceKind.PRIMARY_POST, List.of("时间"), HumorMechanism.LITERAL,
                "笑点分析", "原创示例", 4, true, NOW);
    }

    private static void assertCode(BlogErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(BlogException.class, action::run).code());
    }

    private static final class Fixture {
        final Transactions transactions = new Transactions();
        final Repository repository = new Repository();
        final List<HumorSearchQuery> queries = new ArrayList<>();
        List<HumorSearchHit> hits = List.of();
        final HumorKnowledgeApplicationService service = new HumorKnowledgeApplicationService(repository, query -> {
            queries.add(query);
            return hits;
        }, transactions, CLOCK);

        final class Transactions implements TransactionRunner {
            int calls;
            boolean active;
            @Override public <T> T required(Supplier<T> action) {
                calls++;
                active = true;
                try { return action.get(); }
                finally { active = false; }
            }
        }

        final class Repository implements HumorMaterialRepository {
            final Map<String, HumorMaterial> values = new LinkedHashMap<>();
            int adds;
            int updates;
            boolean simulateConcurrentUpdate;
            @Override public List<HumorMaterial> findAll() {
                assertTrue(transactions.active);
                return List.copyOf(values.values());
            }
            @Override public Optional<HumorMaterial> findById(String id) {
                assertTrue(transactions.active);
                return Optional.ofNullable(values.get(id));
            }
            @Override public void add(HumorMaterial material) {
                assertTrue(transactions.active);
                adds++;
                values.put(material.id(), material);
            }
            @Override public void update(HumorMaterial material) {
                assertTrue(transactions.active);
                HumorMaterial stored = values.get(material.id());
                if (simulateConcurrentUpdate || stored == null || stored.version() != material.version() - 1) {
                    throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "并发更新导致 CAS 失败");
                }
                updates++;
                values.put(material.id(), material);
            }
            @Override public Optional<AgentHumorProfile> findProfile(String agentId) { return Optional.empty(); }
            @Override public List<String> recentlyUsedMaterialIds(String agentId, int limit) { return List.of(); }
            @Override public void recordUse(String runId, List<String> ids, Instant now) {
                throw new AssertionError("维护素材不应记录生成使用次数");
            }
        }
    }
}
