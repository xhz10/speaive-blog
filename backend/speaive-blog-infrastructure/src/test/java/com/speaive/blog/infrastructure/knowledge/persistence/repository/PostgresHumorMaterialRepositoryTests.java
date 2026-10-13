package com.speaive.blog.infrastructure.knowledge.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.ai.AiCommentGeneration;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiCommentPrompt;
import com.speaive.blog.application.port.out.ai.AiSummaryGeneration;
import com.speaive.blog.application.port.out.ai.AiSummaryGenerationPort;
import com.speaive.blog.application.port.out.ai.AiSummaryPrompt;
import com.speaive.blog.application.service.CommentApplicationService;
import com.speaive.blog.domain.knowledge.AgentHumorProfile;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import com.speaive.blog.domain.knowledge.HumorMechanism;
import com.speaive.blog.domain.knowledge.HumorSourceKind;
import com.speaive.blog.domain.post.PostAiSummary;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAgentDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAgentRunDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCommentDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogMediaDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogPostAiSummaryDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogPostDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogAiPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresAgentRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresAgentRunRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresCommentRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresPostAiSummaryRepository;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresPostRepository;
import com.speaive.blog.infrastructure.knowledge.persistence.mapping.HumorPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.knowledge.search.LuceneHumorSearchAdapter;
import com.speaive.blog.infrastructure.transaction.SpringTransactionRunner;
import com.speaive.blog.application.port.out.knowledge.HumorSearchQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Actual migration chain, PostgreSQL arrays, version races, and run-linked usage accounting. */
@Testcontainers
class PostgresHumorMaterialRepositoryTests {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");
    private static DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private PostgresHumorMaterialRepository materials;

    @BeforeAll
    static void migrateProductionChain() {
        source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(new JdbcTemplate(source).queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class))
                .isEqualTo(14);
    }

    @BeforeEach
    void prepare() {
        jdbc = new JdbcTemplate(source);
        jdbc.update("DELETE FROM blog_agent_run_humor_material");
        jdbc.update("DELETE FROM blog_agent_run");
        jdbc.update("DELETE FROM blog_agent_humor_profile");
        jdbc.update("DELETE FROM blog_humor_material");
        jdbc.update("DELETE FROM blog_post");
        jdbc.update("DELETE FROM blog_agent");
        jdbc.update("DELETE FROM blog_user WHERE type = 'AGENT'");
        materials = new PostgresHumorMaterialRepository(jdbc, Mappers.getMapper(HumorPersistenceMapStructMapper.class));
    }

    @Test
    void roundTripsArraysAndEnumsAndRejectsStaleUpdatesWithoutLosingTheWinner() {
        var original = material("literal-01", "缓存过期了要撕掉日期吗？");
        materials.add(original);
        assertThat(materials.findById(original.id())).contains(original);
        assertThat(materials.findById("missing")).isEmpty();
        var changed = revise(original, "缓存不是面包，先检查失效策略。", false);
        materials.update(changed);
        assertThat(materials.findAll()).containsExactly(changed);
        assertThatThrownBy(() -> materials.update(revise(original, "另一个管理员的旧修改", true)))
                .isInstanceOfSatisfying(BlogException.class,
                        error -> assertThat(error.code()).isEqualTo(BlogErrorCode.VERSION_CONFLICT));
        assertThat(materials.findById(original.id())).contains(changed);
    }

    @Test
    void concurrentEditorsCanAdvanceAVersionOnlyOnce() throws Exception {
        var original = material("literal-race", "缓存过期了要撕掉日期吗？");
        materials.add(original);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var left = threads.submit(() -> compete(revise(original, "左边的缓存修改", true), ready, start));
            var right = threads.submit(() -> compete(revise(original, "右边的缓存修改", true), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved", "VERSION_CONFLICT");
        } finally {
            start.countDown();
        }
        var winner = materials.findById(original.id()).orElseThrow();
        assertThat(winner.version()).isEqualTo(2);
        assertThat(winner.excerpt()).isIn("左边的缓存修改", "右边的缓存修改");
    }

    @Test
    void profileBindingIsOptionalAndEnforcedByForeignKeyAndMechanismConstraint() {
        assertThat(materials.findProfile("agent-a")).isEmpty();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO blog_agent_humor_profile VALUES ('missing', 'LITERAL', TRUE)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        addAgent("agent-a");
        jdbc.update("INSERT INTO blog_agent_humor_profile VALUES ('agent-a', 'LITERAL', TRUE)");
        assertThat(materials.findProfile("agent-a")).contains(new AgentHumorProfile("agent-a", HumorMechanism.LITERAL, true));
        jdbc.update("UPDATE blog_agent_humor_profile SET enabled = FALSE WHERE agent_id = 'agent-a'");
        assertThat(materials.findProfile("agent-a").orElseThrow().enabled()).isFalse();
        assertThatThrownBy(() -> jdbc.update("UPDATE blog_agent_humor_profile SET mechanism = 'UNKNOWN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usageKeepsStableOrderIsIdempotentAndIgnoresFailedRunsAndOtherAgents() {
        for (String id : List.of("one", "two", "three")) materials.add(material(id, "缓存示例" + id));
        addAgent("agent-a");
        addAgent("agent-b");
        addPost();
        addRun("success", "agent-a", "SUCCEEDED", NOW);
        materials.recordUse("success", List.of("one", "two"), NOW);
        materials.recordUse("success", List.of("one", "two"), NOW.plusSeconds(10));
        assertThat(jdbc.queryForList("SELECT material_id FROM blog_agent_run_humor_material WHERE run_id = 'success' ORDER BY material_order", String.class))
                .containsExactly("one", "two");
        addRun("failed", "agent-a", "FAILED", NOW.plusSeconds(1));
        materials.recordUse("failed", List.of("three"), NOW.plusSeconds(1));
        addRun("other-agent", "agent-b", "SUCCEEDED", NOW.plusSeconds(2));
        materials.recordUse("other-agent", List.of("three"), NOW.plusSeconds(2));
        assertThat(materials.recentlyUsedMaterialIds("agent-a", 10)).containsExactly("one", "two");
        addRun("running", "agent-a", "RUNNING", NOW.plusSeconds(3));
        materials.recordUse("running", List.of("two", "three"), NOW.plusSeconds(3));
        assertThat(materials.recentlyUsedMaterialIds("agent-a", 2)).containsExactly("two", "three");
        jdbc.update("UPDATE blog_agent_run SET status = 'FAILED' WHERE id = 'running'");
        assertThat(materials.recentlyUsedMaterialIds("agent-a", 10)).containsExactly("one", "two");
        assertThat(materials.recentlyUsedMaterialIds("agent-a", 0)).isEmpty();
    }

    @Test
    void invalidUsageIsAtomicAndAnApplicationRollbackRemovesTheAuditLinks() {
        materials.add(material("one", "缓存材料"));
        addAgent("agent-a");
        addPost();
        addRun("running", "agent-a", "RUNNING", NOW);
        assertThatThrownBy(() -> materials.recordUse("running", List.of("one", "missing"), NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(materials.recentlyUsedMaterialIds("agent-a", 10)).isEmpty();
        var transactions = new SpringTransactionRunner(new DataSourceTransactionManager(source));
        assertThatThrownBy(() -> transactions.required(() -> {
            materials.recordUse("running", List.of("one"), NOW);
            throw new IllegalStateException("roll back generation preparation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(materials.recentlyUsedMaterialIds("agent-a", 10)).isEmpty();
    }

    @Test
    void recentWindowCountsUsageEventsSoOldMaterialsFallOutEvenWhenOneMaterialIsRepeated() {
        materials.add(material("old", "旧的缓存素材"));
        materials.add(material("new", "新的缓存素材"));
        addAgent("agent-a");
        addPost();
        addRun("old-run", "agent-a", "SUCCEEDED", NOW);
        materials.recordUse("old-run", List.of("old"), NOW);
        for (int index = 1; index <= 12; index++) {
            String runId = "repeat-" + index;
            Instant time = NOW.plusSeconds(index);
            addRun(runId, "agent-a", "SUCCEEDED", time);
            materials.recordUse(runId, List.of("new"), time);
        }
        assertThat(materials.recentlyUsedMaterialIds("agent-a", 12)).containsExactly("new");
        assertThat(materials.recentlyUsedMaterialIds("agent-a", 13)).containsExactly("new", "old");
    }

    @Test
    void postgresRejectsInvalidTopicsMetadataAndVersionsEvenOutsideTheDomain() {
        materials.add(material("checks", "缓存材料"));
        for (String assignment : List.of("topics = ARRAY[]::text[]", "topics = ARRAY[NULL]::text[]",
                "topics = ARRAY['']", "topics = ARRAY[repeat('长', 51)]", "topics = array_fill('主题'::text, ARRAY[13])",
                "quality = 6", "version = 0", "source_kind = 'UNKNOWN'", "mechanism = 'UNKNOWN'",
                "excerpt = repeat('长', 501)", "source_url = 'file:///etc/passwd'",
                "updated_at = created_at - interval '1 second'")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE blog_humor_material SET " + assignment + " WHERE id = 'checks'"))
                    .as(assignment).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(materials.findById("checks").orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void realCorpusAndBothSeedScriptsRoundTripAndRetrieveTenChineseTopicsWithoutOverwritingEdits() throws Exception {
        Path root = repositoryRoot();
        String agentSql = Files.readString(root.resolve("scripts/sql/seed-ruozhi-agents.sql"));
        String materialSql = Files.readString(root.resolve("scripts/sql/seed-ruozhi-knowledge.sql"));
        CorpusEntry[] corpus = JsonMapper.builder().build().readValue(
                Files.readString(root.resolve("docs/knowledge/ruozhi-corpus.json")), CorpusEntry[].class);
        assertThat(corpus).hasSize(35);
        jdbc.execute(agentSql);
        jdbc.execute(materialSql);
        assertThat(materials.findAll()).hasSize(35);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_agent_humor_profile", Integer.class)).isEqualTo(5);
        for (HumorMechanism mechanism : HumorMechanism.values()) {
            assertThat(materials.findAll().stream().filter(row -> row.mechanism() == mechanism).toList()).hasSize(7);
        }
        for (CorpusEntry entry : corpus) {
            var actual = materials.findById(entry.id()).orElseThrow();
            assertThat(actual).isEqualTo(HumorMaterial.create(entry.id(), entry.excerpt(), entry.sourceUrl(),
                    entry.sourceTitle(), entry.sourceKind(), entry.topics(), entry.mechanism(), entry.analysis(),
                    entry.adaptation(), entry.quality(), true, actual.createdAt()));
        }
        try (var search = new LuceneHumorSearchAdapter(materials)) {
            var topics = Map.ofEntries(
                    Map.entry("耳机", "literal-earphone-department"),
                    Map.entry("水族馆", "literal-pool-idiom"),
                    Map.entry("陨石", "causal-meteor-destination"),
                    Map.entry("快递", "causal-courier-time"),
                    Map.entry("勾股", "concept-water-pythagoras"),
                    Map.entry("甜甜圈", "concept-donut-decentralized"),
                    Map.entry("拖延", "daily-procrastination-learning"),
                    Map.entry("钱包", "daily-wallet-freedom"),
                    Map.entry("纸飞机", "person-paper-plane-calculation"),
                    Map.entry("天气", "person-cloud-trend"));
            for (var expected : topics.entrySet()) {
                var mechanism = materials.findById(expected.getValue()).orElseThrow().mechanism();
                var hits = search.search(new HumorSearchQuery(expected.getKey(), mechanism, 20));
                assertThat(hits).as("Chinese topic %s", expected.getKey()).isNotEmpty();
                assertThat(hits.getFirst().material().id()).as(expected.getKey()).isEqualTo(expected.getValue());
                assertThat(hits).allSatisfy(hit -> assertThat(hit.material().mechanism()).isEqualTo(mechanism));
            }
            var original = materials.findById("literal-earphone-department").orElseThrow();
            var edited = original.revise("用户维护的设备素材", original.sourceUrl(), original.sourceTitle(),
                    original.sourceKind(), List.of("设备", "修理"), original.mechanism(), original.analysis(),
                    "用户维护的原创迁移示例", 3, false, original.updatedAt().plusSeconds(1));
            materials.update(edited);
            jdbc.update("UPDATE blog_agent_humor_profile SET enabled = FALSE WHERE mechanism = 'LITERAL'");
            jdbc.update("UPDATE blog_user SET status = 'DISABLED' WHERE username = 'rz_wang_laoshi'");
            var before = databaseSnapshot();
            jdbc.execute(agentSql);
            jdbc.execute(materialSql);
            assertThat(databaseSnapshot()).isEqualTo(before);
            assertThat(materials.findById(edited.id())).contains(edited);
            assertThat(search.search(new HumorSearchQuery("耳机", HumorMechanism.LITERAL, 20)))
                    .noneSatisfy(hit -> assertThat(hit.material().id()).isEqualTo(edited.id()));
        }
    }

    @Test
    void realPostgresLuceneAndCommentUseCasePassThreeCorpusReferencesToAiAndPersistTheirRunUsage() throws Exception {
        Path root = repositoryRoot();
        jdbc.execute(Files.readString(root.resolve("scripts/sql/seed-ruozhi-agents.sql")));
        jdbc.execute(Files.readString(root.resolve("scripts/sql/seed-ruozhi-knowledge.sql")));
        addPost();
        jdbc.update("""
                UPDATE blog_post SET title = '耳机、饮料与成语的名字',
                    description = '耳机修理、饮料品牌、水族馆成语，讨论词语的字面误读。',
                    body = '文章讨论耳机名称、饮料品牌以及水族馆的成语解释。'
                WHERE id = 'post-a'
                """);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(BlogPostDatabaseMapper.class, BlogAuthorDatabaseMapper.class,
                BlogMediaDatabaseMapper.class, BlogAgentDatabaseMapper.class, BlogCommentDatabaseMapper.class,
                BlogAgentRunDatabaseMapper.class, BlogPostAiSummaryDatabaseMapper.class)) {
            configuration.addMapper(mapper);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject());
        var aiMapping = Mappers.getMapper(BlogAiPersistenceMapStructMapper.class);
        var authors = sessions.getMapper(BlogAuthorDatabaseMapper.class);
        var posts = new PostgresPostRepository(sessions.getMapper(BlogPostDatabaseMapper.class), authors,
                sessions.getMapper(BlogMediaDatabaseMapper.class), Mappers.getMapper(BlogPersistenceMapStructMapper.class));
        var summaries = new PostgresPostAiSummaryRepository(sessions.getMapper(BlogPostAiSummaryDatabaseMapper.class), aiMapping);
        var agents = new PostgresAgentRepository(sessions.getMapper(BlogAgentDatabaseMapper.class), authors, aiMapping);
        var comments = new PostgresCommentRepository(sessions.getMapper(BlogCommentDatabaseMapper.class), authors, aiMapping);
        var runs = new PostgresAgentRunRepository(sessions.getMapper(BlogAgentRunDatabaseMapper.class), aiMapping);
        summaries.save(PostAiSummary.create("post-a", 1, "耳机、饮料、水族馆、成语和名字中的字面误读。",
                "test-summary-model", null, null, NOW));
        var captured = new AtomicReference<AiCommentPrompt>();
        AiCommentGenerationPort ai = new AiCommentGenerationPort() {
            @Override public boolean isAvailable() { return true; }
            @Override public AiCommentGeneration generate(AiCommentPrompt prompt) {
                captured.set(prompt);
                assertThat(prompt.humorReferences()).hasSize(3);
                assertThat(prompt.humorReferences()).extracting(AiCommentPrompt.HumorReference::id).doesNotHaveDuplicates();
                for (var reference : prompt.humorReferences()) {
                    var stored = materials.findById(reference.id()).orElseThrow();
                    assertThat(stored.mechanism()).isEqualTo(prompt.replyTarget() == null
                            ? HumorMechanism.LITERAL : HumorMechanism.PERSONIFICATION);
                    assertThat(reference.sourceUrl()).isEqualTo(stored.sourceUrl());
                    assertThat(reference.excerpt()).isEqualTo(stored.excerpt());
                    assertThat(reference.analysis()).isEqualTo(stored.analysis());
                    assertThat(reference.adaptation()).isEqualTo(stored.adaptation());
                }
                assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_agent_run WHERE status = 'RUNNING'", Integer.class))
                        .isEqualTo(1);
                assertThat(jdbc.queryForList("""
                        SELECT usage.material_id FROM blog_agent_run_humor_material usage
                        JOIN blog_agent_run run ON run.id = usage.run_id
                        WHERE run.status = 'RUNNING' ORDER BY usage.material_order
                        """, String.class))
                        .containsExactlyElementsOf(prompt.humorReferences().stream().map(AiCommentPrompt.HumorReference::id).toList());
                return new AiCommentGeneration("耳机的名字这么认真，维修的时候大概也希望先点一次名。", "fake-ai-model", 12, 18);
            }
        };
        AiSummaryGenerationPort summaryAi = new AiSummaryGenerationPort() {
            @Override public boolean isAvailable() { return true; }
            @Override public AiSummaryGeneration generate(AiSummaryPrompt prompt) {
                throw new IllegalStateException("The test already stores a summary for the current revision");
            }
        };
        String agentId = "fda22f13-deb5-5477-a7f9-ab8fc5c00995";
        try (var search = new LuceneHumorSearchAdapter(materials)) {
            var service = new CommentApplicationService(posts, summaries, agents, comments, runs, ai,
                    summaryAi, new SpringTransactionRunner(new DataSourceTransactionManager(source)),
                    Clock.fixed(NOW, ZoneOffset.UTC), materials, search);
            var result = service.generateAiComment("post-a", agentId);
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.author().id()).isEqualTo(agentId);
            assertThat(captured.get().title()).isEqualTo("耳机、饮料与成语的名字");
            assertThat(jdbc.queryForObject("SELECT status FROM blog_agent_run", String.class)).isEqualTo("SUCCEEDED");
            assertThat(jdbc.queryForObject("SELECT comment_id FROM blog_agent_run", String.class)).isEqualTo(result.id());
            assertThat(materials.recentlyUsedMaterialIds(agentId, 10)).containsExactlyElementsOf(
                    captured.get().humorReferences().stream().map(AiCommentPrompt.HumorReference::id).toList());
            assertThat(jdbc.queryForObject("SELECT body FROM blog_comment WHERE id = ?", String.class, result.id()))
                    .isEqualTo(result.body());

            // More than 64 unrelated terms would exhaust the search cap if the reply target were appended last.
            String longDescription = java.util.stream.IntStream.range(0, 75)
                    .mapToObj(index -> "w" + index).collect(java.util.stream.Collectors.joining(" "));
            jdbc.update("UPDATE blog_post SET title = ?, description = ?, revision = 2 WHERE id = 'post-a'",
                    "数据库网络性能".repeat(20), longDescription);
            summaries.save(PostAiSummary.create("post-a", 2, longDescription, "test-summary-model", null, null, NOW));
            jdbc.update("""
                    INSERT INTO blog_comment (id, post_id, author_id, body, status, created_at, updated_at)
                    VALUES ('reply-target', 'post-a', '00000000-0000-0000-0000-000000000001',
                        '月牙把不满写在脸上，天空的表情，也像云跟风、灯开关的日出日落。', 'PUBLISHED', ?, ?)
                    """, Timestamp.from(NOW), Timestamp.from(NOW));
            String moonAgentId = "da9dbf77-6035-5cd5-96d0-862c3f239954";
            var reply = service.generateAiReply("reply-target", moonAgentId);
            assertThat(reply.parentCommentId()).isEqualTo("reply-target");
            assertThat(reply.status()).isEqualTo("PENDING");
            assertThat(captured.get().replyTarget().body()).contains("月牙");
            assertThat(captured.get().humorReferences()).extracting(AiCommentPrompt.HumorReference::id)
                    .contains("person-moon-frown");
            assertThat(materials.recentlyUsedMaterialIds(moonAgentId, 10)).containsExactlyElementsOf(
                    captured.get().humorReferences().stream().map(AiCommentPrompt.HumorReference::id).toList());
            assertThat(jdbc.queryForObject("SELECT status FROM blog_agent_run WHERE target_comment_id = 'reply-target'", String.class))
                    .isEqualTo("SUCCEEDED");
        }
    }

    private List<String> databaseSnapshot() {
        return List.of("blog_humor_material", "blog_agent_humor_profile", "blog_user", "blog_agent").stream()
                .flatMap(table -> jdbc.queryForList("SELECT to_jsonb(row)::text FROM " + table
                        + " row ORDER BY to_jsonb(row)::text", String.class).stream()).toList();
    }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("scripts/sql/seed-ruozhi-agents.sql"))) return path;
            path = path.getParent();
        }
        throw new IllegalStateException("Cannot find real repository seed SQL files from test directory");
    }

    private record CorpusEntry(String id, String excerpt, String sourceUrl, String sourceTitle,
            HumorSourceKind sourceKind, List<String> topics, HumorMechanism mechanism,
            String analysis, String adaptation, int quality) {
    }

    private String compete(HumorMaterial next, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race did not start");
        try { materials.update(next); return "saved"; }
        catch (BlogException error) { return error.code().name(); }
    }

    private static HumorMaterial material(String id, String excerpt) {
        return HumorMaterial.create(id, excerpt, "https://tieba.baidu.com/p/1", "公开素材示例", HumorSourceKind.PRIMARY_POST,
                List.of("缓存", "时间"), HumorMechanism.LITERAL, "把缓存过期理解成食物日期。", "把文章中的一个期限理解成日期标签。", 4, true, NOW);
    }

    private static HumorMaterial revise(HumorMaterial before, String excerpt, boolean enabled) {
        return before.revise(excerpt, before.sourceUrl(), before.sourceTitle(), before.sourceKind(), before.topics(),
                before.mechanism(), before.analysis(), before.adaptation(), before.quality(), enabled, NOW.plusSeconds(1));
    }

    private void addAgent(String id) {
        jdbc.update("""
                INSERT INTO blog_user (id, username, display_name, type, status, created_at, updated_at)
                VALUES (?, ?, '测试角色', 'AGENT', 'ACTIVE', ?, ?)
                """, id, id, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO blog_agent (id, system_prompt, created_at, updated_at, reviewed_at)
                VALUES (?, '测试角色提示', ?, ?, ?)
                """, id, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private void addPost() {
        jdbc.update("""
                INSERT INTO blog_post (id, slug, title, description, published_at, updated_at, status,
                    body, revision, created_at, author_id, visibility)
                VALUES ('post-a', 'post-a', '缓存文章', '', ?, ?, 'PUBLISHED', '公开正文', 1, ?,
                    '00000000-0000-0000-0000-000000000001', 'PUBLIC')
                """, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private void addRun(String id, String agentId, String status, Instant time) {
        jdbc.update("""
                INSERT INTO blog_agent_run (id, post_id, post_revision, agent_id, prompt_version, status, started_at)
                VALUES (?, 'post-a', ?, ?, 1, ?, ?)
                """, id, time.getEpochSecond() - NOW.getEpochSecond() + 1, agentId, status, Timestamp.from(time));
    }
}
