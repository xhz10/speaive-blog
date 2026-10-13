package com.speaive.blog.infrastructure.knowledge.search;

import com.speaive.blog.application.port.out.knowledge.HumorSearchHit;
import com.speaive.blog.application.port.out.knowledge.HumorSearchQuery;
import com.speaive.blog.application.port.out.persistence.HumorMaterialRepository;
import com.speaive.blog.domain.knowledge.AgentHumorProfile;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import com.speaive.blog.domain.knowledge.HumorMechanism;
import com.speaive.blog.domain.knowledge.HumorSourceKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuceneHumorSearchAdapterTests {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

    @Test
    void tokenizesChineseWithTheSameAnalyzerForIndexAndQueryAndFiltersMechanismsAndEnablement() {
        var rows = new Materials(List.of(
                material("literal", "数据库查询需要多久？", List.of("数据库", "查询"), HumorMechanism.LITERAL, true),
                material("causal", "数据库查询总在下班之后完成。", List.of("数据库"), HumorMechanism.CAUSAL_REVERSAL, true),
                material("disabled", "数据库查询速度很快。", List.of("数据库"), HumorMechanism.LITERAL, false)));
        try (var search = new LuceneHumorSearchAdapter(rows)) {
            var literal = search.search(new HumorSearchQuery("数据库查询", HumorMechanism.LITERAL, 20));
            assertThat(literal).extracting(hit -> hit.material().id()).containsExactly("literal");
            assertThat(literal.getFirst().score()).isPositive();
            assertThat(search.search(new HumorSearchQuery("数据库查询", HumorMechanism.CAUSAL_REVERSAL, 20)))
                    .extracting(hit -> hit.material().id()).containsExactly("causal");
            assertThat(search.search(new HumorSearchQuery("数据库查询", null, 20)))
                    .extracting(hit -> hit.material().id()).containsExactlyInAnyOrder("literal", "causal");
        }
    }

    @Test
    void topicMatchesReceiveExtraWeightAndZeroOverlapDoesNotBecomeARandomRecommendation() {
        var rows = new Materials(List.of(
                material("topic-hit", "缓存需要刷新。", List.of("缓存"), HumorMechanism.LITERAL, true),
                material("excerpt-hit", "缓存需要刷新。", List.of("足球"), HumorMechanism.LITERAL, true)));
        try (var search = new LuceneHumorSearchAdapter(rows)) {
            var hits = search.search(new HumorSearchQuery("缓存", HumorMechanism.LITERAL, 20));
            assertThat(hits).extracting(hit -> hit.material().id()).containsExactly("topic-hit", "excerpt-hit");
            assertThat(hits.getFirst().score()).isGreaterThan(hits.getLast().score());
            assertThat(search.search(new HumorSearchQuery("榴莲", HumorMechanism.LITERAL, 20))).isEmpty();
            assertThat(search.search(new HumorSearchQuery("榴莲。", HumorMechanism.LITERAL, 20))).isEmpty();
            assertThat(search.search(new HumorSearchQuery("。？！,.;:()", null, 20))).isEmpty();
            assertThat(search.search(new HumorSearchQuery("", null, 20))).isEmpty();
        }
        try (var empty = new LuceneHumorSearchAdapter(new Materials(List.of()))) {
            assertThat(empty.search(new HumorSearchQuery("缓存", HumorMechanism.LITERAL, 20))).isEmpty();
        }
    }

    @Test
    void punctuationStopListKeepsChineseNegationAsMeaningfulQueryTerms() {
        var rows = new Materials(List.of(
                material("not", "我不快乐。", List.of("否定"), HumorMechanism.LITERAL, true),
                material("none", "我没快乐。", List.of("缺少"), HumorMechanism.LITERAL, true),
                material("positive", "我快乐。", List.of("肯定"), HumorMechanism.LITERAL, true)));
        try (var search = new LuceneHumorSearchAdapter(rows)) {
            assertThat(search.search(new HumorSearchQuery("不。", null, 20)))
                    .extracting(hit -> hit.material().id()).containsExactly("not");
            assertThat(search.search(new HumorSearchQuery("没。", null, 20)))
                    .extracting(hit -> hit.material().id()).containsExactly("none");
        }
    }

    @Test
    void nextLookupSeesEditedTextTopicsQualityAndEnablementEvenWhenVersionWasNotAdvanced() {
        var original = material("editable", "缓存需要刷新。", List.of("缓存"), HumorMechanism.LITERAL, true);
        var rows = new Materials(List.of(original));
        try (var search = new LuceneHumorSearchAdapter(rows)) {
            assertThat(search.search(new HumorSearchQuery("缓存", null, 20))).hasSize(1);
            var changed = new HumorMaterial(original.id(), "足球踢进球门。", original.sourceUrl(), original.sourceTitle(),
                    original.sourceKind(), List.of("足球"), original.mechanism(), original.analysis(), original.adaptation(),
                    5, true, original.version(), original.createdAt(), original.updatedAt());
            rows.rows.set(List.of(changed));
            assertThat(search.search(new HumorSearchQuery("缓存", null, 20))).isEmpty();
            var hits = search.search(new HumorSearchQuery("足球", null, 20));
            assertThat(hits).extracting(HumorSearchHit::material).containsExactly(changed);
            rows.rows.set(List.of(changed.revise(changed.excerpt(), changed.sourceUrl(), changed.sourceTitle(),
                    changed.sourceKind(), changed.topics(), changed.mechanism(), changed.analysis(), changed.adaptation(),
                    changed.quality(), false, NOW.plusSeconds(1))));
            assertThat(search.search(new HumorSearchQuery("足球", null, 20))).isEmpty();
            rows.rows.set(List.of());
            assertThat(search.search(new HumorSearchQuery("足球", null, 20))).isEmpty();
        }
    }

    @Test
    void retrievalCapsCandidatesAtTwentyAndKeepsTiesDeterministic() {
        var rows = new ArrayList<HumorMaterial>();
        for (int index = 24; index > 0; index--) {
            rows.add(material("item-%02d".formatted(index), "缓存需要刷新。", List.of("缓存"), HumorMechanism.LITERAL, true));
        }
        try (var search = new LuceneHumorSearchAdapter(new Materials(rows))) {
            var all = search.search(new HumorSearchQuery("缓存", null, 50));
            assertThat(all).hasSize(20);
            assertThat(all).extracting(hit -> hit.material().id()).isSorted();
            assertThat(search.search(new HumorSearchQuery("缓存", null, 3))).hasSize(3);
        }
    }

    @Test
    void readersAndRefreshersShareWholeSnapshotsWithoutClosingActiveReaders() throws Exception {
        var left = material("left", "缓存左边刷新。", List.of("缓存"), HumorMechanism.LITERAL, true);
        var right = material("right", "缓存右边刷新。", List.of("缓存"), HumorMechanism.LITERAL, true);
        var rows = new Materials(List.of(left));
        try (var search = new LuceneHumorSearchAdapter(rows);
                var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 6; worker++) {
                tasks.add(threads.submit(() -> {
                    for (int round = 0; round < 30; round++) {
                        var hits = search.search(new HumorSearchQuery("缓存", HumorMechanism.LITERAL, 20));
                        assertThat(hits).hasSize(1);
                        assertThat(hits.getFirst().material().id()).isIn("left", "right");
                    }
                }));
            }
            tasks.add(threads.submit(() -> {
                for (int round = 0; round < 30; round++) rows.rows.set(List.of(round % 2 == 0 ? right : left));
            }));
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void closeWaitsForAnInProgressLookupIsIdempotentAndRejectsFurtherLookups() throws Exception {
        var insideSnapshot = new CountDownLatch(1);
        var releaseSnapshot = new CountDownLatch(1);
        var closeStarted = new CountDownLatch(1);
        var rows = new Materials(List.of(material("one", "缓存需要刷新。", List.of("缓存"), HumorMechanism.LITERAL, true)));
        rows.beforeRead = () -> {
            insideSnapshot.countDown();
            try {
                if (!releaseSnapshot.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("lookup was not released");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            return true;
        };
        var search = new LuceneHumorSearchAdapter(rows);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var lookup = threads.submit(() -> search.search(new HumorSearchQuery("缓存", null, 20)));
            assertThat(insideSnapshot.await(5, TimeUnit.SECONDS)).isTrue();
            var closing = threads.submit(() -> { closeStarted.countDown(); search.close(); });
            assertThat(closeStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(closing.isDone()).isFalse();
            releaseSnapshot.countDown();
            assertThat(lookup.get(10, TimeUnit.SECONDS)).hasSize(1);
            closing.get(10, TimeUnit.SECONDS);
            search.close();
            assertThatThrownBy(() -> search.search(new HumorSearchQuery("缓存", null, 20)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
        } finally {
            releaseSnapshot.countDown();
            search.close();
        }
    }

    private static HumorMaterial material(String id, String excerpt, List<String> topics, HumorMechanism mechanism, boolean enabled) {
        return HumorMaterial.create(id, excerpt, "https://tieba.baidu.com/p/1", "公开素材", HumorSourceKind.PRIMARY_POST,
                topics, mechanism, "只把一个普通动作解释歪。", "围绕文章细节做一次意外转折。", 4, enabled, NOW);
    }

    private static final class Materials implements HumorMaterialRepository {
        final AtomicReference<List<HumorMaterial>> rows;
        Supplier<Boolean> beforeRead = () -> true;

        Materials(List<HumorMaterial> rows) { this.rows = new AtomicReference<>(List.copyOf(rows)); }
        @Override public List<HumorMaterial> findAll() { beforeRead.get(); return rows.get(); }
        @Override public Optional<HumorMaterial> findById(String id) { return rows.get().stream().filter(row -> row.id().equals(id)).findFirst(); }
        @Override public void add(HumorMaterial material) { throw new UnsupportedOperationException(); }
        @Override public void update(HumorMaterial material) { throw new UnsupportedOperationException(); }
        @Override public Optional<AgentHumorProfile> findProfile(String agentId) { return Optional.empty(); }
        @Override public List<String> recentlyUsedMaterialIds(String agentId, int limit) { return List.of(); }
        @Override public void recordUse(String runId, List<String> materialIds, Instant now) { throw new UnsupportedOperationException(); }
    }
}
