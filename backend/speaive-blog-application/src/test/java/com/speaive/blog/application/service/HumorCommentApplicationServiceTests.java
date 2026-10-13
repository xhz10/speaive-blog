package com.speaive.blog.application.service;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.ai.AiCommentGeneration;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiCommentPrompt;
import com.speaive.blog.application.port.out.ai.AiSummaryGeneration;
import com.speaive.blog.application.port.out.ai.AiSummaryGenerationPort;
import com.speaive.blog.application.port.out.ai.AiSummaryPrompt;
import com.speaive.blog.application.port.out.knowledge.HumorSearchHit;
import com.speaive.blog.application.port.out.knowledge.HumorSearchQuery;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.persistence.AgentRunRepository;
import com.speaive.blog.application.port.out.persistence.CommentRepository;
import com.speaive.blog.application.port.out.persistence.HumorMaterialRepository;
import com.speaive.blog.application.port.out.persistence.PostAiSummaryRepository;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.agent.AgentRun;
import com.speaive.blog.domain.agent.AgentRunStatus;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.comment.Comment;
import com.speaive.blog.domain.knowledge.AgentHumorProfile;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import com.speaive.blog.domain.knowledge.HumorMechanism;
import com.speaive.blog.domain.knowledge.HumorSourceKind;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostAiSummary;
import com.speaive.blog.domain.post.PostContent;
import com.speaive.blog.domain.post.PostVisibility;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumorCommentApplicationServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String AGENT_ID = "10000000-0000-0000-0000-000000000001";

    @Test
    void boundAgentRetrievesFromCurrentContextAndRecordsReferencesBeforeCallingModel() {
        Fixture fixture = new Fixture();
        fixture.hits = List.of(hit("first", 5, 2));

        var result = fixture.service().generateAiComment("salary", AGENT_ID);

        assertEquals("PENDING", result.status());
        assertEquals(AGENT_ID, fixture.knowledge.profileLookups.getFirst());
        assertEquals(HumorMechanism.LITERAL, fixture.queries.getFirst().mechanism());
        assertEquals(20, fixture.queries.getFirst().limit());
        assertTrue(fixture.queries.getFirst().text().contains("工资要过期了"));
        assertTrue(fixture.queries.getFirst().text().contains("简介关键词"));
        assertTrue(fixture.queries.getFirst().text().contains("当前摘要关键词"));
        assertTrue(fixture.queries.getFirst().text().contains("期限 失效 有效期"));
        assertFalse(fixture.queries.getFirst().text().contains("正文不应用于检索"));
        assertEquals(List.of("first"), fixture.captured.humorReferences().stream()
                .map(AiCommentPrompt.HumorReference::id).toList());
        Use use = fixture.uses.getFirst();
        assertEquals(fixture.runs.values().iterator().next().id(), use.runId());
        assertEquals(List.of("first"), use.materialIds());
        assertEquals(NOW, use.at());
    }

    @Test
    void unboundAndDisabledProfilesLeaveOrdinaryAgentsUnchanged() {
        for (AgentHumorProfile profile : java.util.Arrays.asList(null,
                new AgentHumorProfile(AGENT_ID, HumorMechanism.LITERAL, false))) {
            Fixture fixture = new Fixture();
            fixture.knowledge.profile = profile;

            fixture.service().generateAiComment("salary", AGENT_ID);

            assertTrue(fixture.queries.isEmpty());
            assertEquals(0, fixture.knowledge.recentLookups);
            assertTrue(fixture.uses.isEmpty());
            assertTrue(fixture.captured.humorReferences().isEmpty());
        }
    }

    @Test
    void zeroSearchHitsStillProduceNormalCandidateWithoutUsageRows() {
        Fixture fixture = new Fixture();

        fixture.service().generateAiComment("salary", AGENT_ID);

        assertEquals(1, fixture.queries.size());
        assertTrue(fixture.captured.humorReferences().isEmpty());
        assertTrue(fixture.uses.isEmpty());
        assertEquals(AgentRunStatus.SUCCEEDED, fixture.runs.values().iterator().next().status());
    }

    @Test
    void qualityAndRecentUseRerankPositiveHitsWithDistinctEnabledMaterials() {
        Fixture fixture = new Fixture();
        fixture.knowledge.recent = List.of("recent");
        HumorMaterial duplicateText = HumorMaterial.create("duplicate-text", "素材 high 的原句",
                "https://example.com/duplicate", "测试来源", HumorSourceKind.PRIMARY_POST,
                List.of("工资"), HumorMechanism.LITERAL, "分析", "原创示例", 5, true, NOW);
        HumorMaterial wrongMechanism = HumorMaterial.create("wrong-mechanism", "生活素材",
                "https://example.com/wrong", "测试来源", HumorSourceKind.PRIMARY_POST,
                List.of("工资"), HumorMechanism.DAILY_LIFE, "分析", "原创示例", 5, true, NOW);
        HumorMaterial disabled = HumorMaterial.create("disabled", "停用素材",
                "https://example.com/disabled", "测试来源", HumorSourceKind.PRIMARY_POST,
                List.of("工资"), HumorMechanism.LITERAL, "分析", "原创示例", 5, false, NOW);
        fixture.hits = List.of(hit("recent", 5, 2.3), hit("low", 1, 1), hit("high", 5, 1),
                hit("ordinary", 3, 1.2), hit("high", 5, 0.8), new HumorSearchHit(duplicateText, 0.9),
                new HumorSearchHit(wrongMechanism, 100), new HumorSearchHit(disabled, 100),
                hit("zero", 5, 0), hit("invalid", 5, Double.NaN));

        fixture.service().generateAiComment("salary", AGENT_ID);

        assertEquals(List.of("high", "ordinary", "recent"), fixture.captured.humorReferences().stream()
                .map(AiCommentPrompt.HumorReference::id).toList());
        assertEquals(3, fixture.uses.getFirst().materialIds().size());
    }

    @Test
    void replyQueryKeepsTargetAndCapsLongCurrentSummary() {
        Fixture fixture = new Fixture();
        fixture.summary = PostAiSummary.create(fixture.post.id(), fixture.post.revision(),
                "当前摘要" + "长".repeat(990), "test", 1, 1, NOW);
        AgentProfile other = AgentProfile.create("20000000-0000-0000-0000-000000000001", "other-agent",
                "其他角色", null, "其他人设", null, 0.8, false, true, NOW);
        Comment target = Comment.createAiCandidate("30000000-0000-0000-0000-000000000001",
                fixture.post.id(), other.identity(), "回复目标关键词：熬夜换来的工资还能保鲜吗？", NOW).publish(NOW);
        fixture.comments.put(target.id(), target);

        fixture.service().generateAiReply(target.id(), AGENT_ID);

        String query = fixture.queries.getFirst().text();
        assertTrue(query.contains("回复目标关键词"));
        assertTrue(query.indexOf("回复目标关键词") < query.indexOf("简介关键词"));
        assertTrue(query.indexOf("期限 失效 有效期") < query.indexOf("工资要过期了"));
        assertTrue(query.contains("当前摘要"));
        assertTrue(query.contains("睡觉 睡眠 熬夜 失眠"));
        assertTrue(query.codePointCount(0, query.length()) <= 1_000);
        assertEquals(target.id(), fixture.captured.replyTarget().id());
        assertEquals(target.id(), fixture.runs.values().iterator().next().targetCommentId());
    }

    @Test
    void usageFailureRollsBackRunAndUsageTogetherWithoutCallingModel() {
        Fixture fixture = new Fixture();
        fixture.hits = List.of(hit("first", 5, 2));
        fixture.knowledge.failRecordUse = true;

        assertThrows(IllegalStateException.class,
                () -> fixture.service().generateAiComment("salary", AGENT_ID));

        assertTrue(fixture.runs.isEmpty());
        assertTrue(fixture.uses.isEmpty());
        assertEquals(0, fixture.aiCalls);
    }

    @Test
    void modelFailureKeepsReferencesAuditedAndMarksExistingRunFailed() {
        Fixture fixture = new Fixture();
        fixture.hits = List.of(hit("first", 5, 2));
        fixture.failModel = true;

        BlogException exception = assertThrows(BlogException.class,
                () -> fixture.service().generateAiComment("salary", AGENT_ID));

        assertEquals(BlogErrorCode.AI_GENERATION_FAILED, exception.code());
        assertEquals(AgentRunStatus.FAILED, fixture.runs.values().iterator().next().status());
        assertEquals(List.of("first"), fixture.uses.getFirst().materialIds());
        assertTrue(fixture.comments.isEmpty());
    }

    private static HumorSearchHit hit(String id, int quality, double score) {
        return new HumorSearchHit(HumorMaterial.create(id, "素材 " + id + " 的原句",
                "https://example.com/" + id, "测试来源", HumorSourceKind.PRIMARY_POST,
                List.of("工资"), HumorMechanism.LITERAL, "把工资的有效期当作食品保质期", "原创示例 " + id,
                quality, true, NOW), score);
    }

    private record Use(String runId, List<String> materialIds, Instant at) {}

    private static final class Fixture {
        final Post post = Post.createDraft("40000000-0000-0000-0000-000000000001", "salary",
                new PostContent("工资要过期了", "简介关键词", NOW, List.of(), null, "正文不应用于检索"),
                Author.ADMIN, PostVisibility.PUBLIC, NOW);
        final AgentProfile agent = AgentProfile.create(AGENT_ID, "renamed-agent", "随时可改的展示名",
                null, "保持人设", null, 0.8, false, true, NOW);
        PostAiSummary summary = PostAiSummary.create(post.id(), post.revision(), "当前摘要关键词",
                "test", 1, 1, NOW);
        final Map<String, AgentRun> runs = new HashMap<>();
        final Map<String, Comment> comments = new HashMap<>();
        final List<Use> uses = new ArrayList<>();
        final List<HumorSearchQuery> queries = new ArrayList<>();
        final Knowledge knowledge = new Knowledge();
        final SnapshotTransactions transactions = new SnapshotTransactions();
        List<HumorSearchHit> hits = List.of();
        AiCommentPrompt captured;
        int aiCalls;
        boolean failModel;

        CommentApplicationService service() {
            PostRepository posts = proxy(PostRepository.class, (object, method, args) -> switch (method.getName()) {
                case "findBySlug", "findById" -> Optional.of(post);
                case "findAll" -> List.of();
                default -> throw new UnsupportedOperationException(method.getName());
            });
            PostAiSummaryRepository summaries = proxy(PostAiSummaryRepository.class,
                    (object, method, args) -> switch (method.getName()) {
                        case "findByPostId" -> Optional.of(summary);
                        case "findByPostIds" -> List.of();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            AgentRepository agents = proxy(AgentRepository.class, (object, method, args) -> {
                if (method.getName().equals("findById")) return Optional.of(agent);
                throw new UnsupportedOperationException(method.getName());
            });
            CommentRepository commentRepository = proxy(CommentRepository.class,
                    (object, method, args) -> switch (method.getName()) {
                        case "findByPostId" -> new ArrayList<>(comments.values());
                        case "findById" -> Optional.ofNullable(comments.get((String) args[0]));
                        case "add" -> { Comment item = (Comment) args[0]; comments.put(item.id(), item); yield null; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            AgentRunRepository runRepository = new AgentRunRepository() {
                @Override public void add(AgentRun run) {
                    assertTrue(transactions.active);
                    runs.put(run.id(), run);
                }
                @Override public void save(AgentRun run) { runs.put(run.id(), run); }
                @Override public void failStaleRunning(String postId, long revision, String agentId,
                        String targetId, Instant before, Instant at) {}
            };
            AiCommentGenerationPort ai = new AiCommentGenerationPort() {
                @Override public boolean isAvailable() { return true; }
                @Override public AiCommentGeneration generate(AiCommentPrompt prompt) {
                    assertFalse(transactions.active);
                    if (!prompt.humorReferences().isEmpty()) assertFalse(uses.isEmpty());
                    captured = prompt;
                    aiCalls++;
                    if (failModel) throw new IllegalStateException("模型断线");
                    return new AiCommentGeneration("工资还能放冰箱延长保质期吗？", "test", 1, 1);
                }
            };
            AiSummaryGenerationPort summaryAi = new AiSummaryGenerationPort() {
                @Override public boolean isAvailable() { return true; }
                @Override public AiSummaryGeneration generate(AiSummaryPrompt prompt) {
                    throw new AssertionError("已有当前摘要，不应请求模型生成新摘要");
                }
            };
            return new CommentApplicationService(posts, summaries, agents, commentRepository, runRepository,
                    ai, summaryAi, transactions, CLOCK, knowledge, query -> {
                        queries.add(query);
                        return hits;
                    });
        }

        final class SnapshotTransactions implements TransactionRunner {
            boolean active;
            @Override public <T> T required(Supplier<T> action) {
                Map<String, AgentRun> oldRuns = new HashMap<>(runs);
                Map<String, Comment> oldComments = new HashMap<>(comments);
                List<Use> oldUses = List.copyOf(uses);
                active = true;
                try {
                    return action.get();
                } catch (RuntimeException exception) {
                    runs.clear(); runs.putAll(oldRuns);
                    comments.clear(); comments.putAll(oldComments);
                    uses.clear(); uses.addAll(oldUses);
                    throw exception;
                } finally {
                    active = false;
                }
            }
        }

        final class Knowledge implements HumorMaterialRepository {
            AgentHumorProfile profile = new AgentHumorProfile(AGENT_ID, HumorMechanism.LITERAL, true);
            List<String> recent = List.of();
            final List<String> profileLookups = new ArrayList<>();
            int recentLookups;
            boolean failRecordUse;
            @Override public List<HumorMaterial> findAll() { return List.of(); }
            @Override public Optional<HumorMaterial> findById(String id) { return Optional.empty(); }
            @Override public void add(HumorMaterial material) { throw new UnsupportedOperationException(); }
            @Override public void update(HumorMaterial material) { throw new UnsupportedOperationException(); }
            @Override public Optional<AgentHumorProfile> findProfile(String agentId) {
                profileLookups.add(agentId);
                return Optional.ofNullable(profile);
            }
            @Override public List<String> recentlyUsedMaterialIds(String agentId, int limit) {
                assertEquals(12, limit);
                recentLookups++;
                return recent;
            }
            @Override public void recordUse(String runId, List<String> materialIds, Instant at) {
                assertTrue(transactions.active);
                assertTrue(runs.containsKey(runId));
                uses.add(new Use(runId, List.copyOf(materialIds), at));
                if (failRecordUse) throw new IllegalStateException("素材审计写入失败");
            }
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
