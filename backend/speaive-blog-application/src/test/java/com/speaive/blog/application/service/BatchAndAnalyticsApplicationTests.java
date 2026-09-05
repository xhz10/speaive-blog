package com.speaive.blog.application.service;

import com.speaive.blog.application.command.comment.GenerateCommentBatchCommand;
import com.speaive.blog.application.command.analytics.RecordArticleVisitCommand;
import com.speaive.blog.application.error.*;
import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.application.port.out.ai.AiGenerationTaskRunner;
import com.speaive.blog.application.port.out.analytics.VisitorContextPort;
import com.speaive.blog.application.port.out.persistence.*;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.query.analytics.VisitAnalyticsQuery;
import com.speaive.blog.application.result.comment.CommentResult;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.analytics.*;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.post.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class BatchAndAnalyticsApplicationTests {
    private static final Instant NOW = Instant.parse("2026-09-06T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TransactionRunner TX = new TransactionRunner() {
        public <T> T required(Supplier<T> action) { return action.get(); }
    };
    private static Post post(PostVisibility visibility) {
        return Post.createDraft("post-id", "story", new PostContent("文章", "", NOW, List.of(), null, "正文"), Author.ADMIN, visibility, NOW);
    }

    @Test
    void batchWarmsOnceAndReturnsRoleFailuresInSelectionOrder() {
        AtomicInteger warmed = new AtomicInteger();
        AtomicInteger generated = new AtomicInteger();
        var agents = fake(AgentRepository.class, (method, args) -> Optional.of(AgentProfile.create(
                (String) args[0], "reader", "读者", null, "认真阅读文章。", null, .7, false, true, NOW)));
        var posts = fake(PostRepository.class, (method, args) -> Optional.of(post(PostVisibility.PUBLIC)));
        var comments = fake(CommentUseCase.class, (method, args) -> {
            if (method.equals("generateAiSummary")) { warmed.incrementAndGet(); return null; }
            assertEquals(1, warmed.get()); generated.incrementAndGet();
            if (args[1].equals("broken")) throw new BlogException(BlogErrorCode.AI_GENERATION_FAILED, "角色失败");
            return new CommentResult("comment", null, null, "候选评论", "PENDING", NOW, NOW);
        });
        var service = new CommentBatchApplicationService(comments, posts, agents, TX, sequentialRunner());
        var result = service.generate("story", new GenerateCommentBatchCommand(List.of("good", "broken")));
        assertEquals(List.of("good", "broken"), result.items().stream().map(item -> item.agentId()).toList());
        assertNotNull(result.items().getFirst().comment());
        assertEquals("AI_GENERATION_FAILED", result.items().getLast().errorCode());
        assertEquals(2, generated.get());
        assertEquals(1, warmed.get());
    }

    @Test
    void batchDoesNotSendPrivateContentWhenNoSelectedRoleIsEntitled() {
        var agents = fake(AgentRepository.class, (method, args) -> Optional.of(AgentProfile.create(
                "reader", "reader", "读者", null, "认真阅读文章。", null, .7, false, true, NOW)));
        var posts = fake(PostRepository.class, (method, args) -> Optional.of(post(PostVisibility.ADMIN_ONLY)));
        var comments = fake(CommentUseCase.class, (method, args) -> { throw new AssertionError("不能调用 AI"); });
        var service = new CommentBatchApplicationService(comments, posts, agents, TX, sequentialRunner());
        var result = service.generate("story", new GenerateCommentBatchCommand(List.of("reader")));
        assertEquals("INVALID_REQUEST", result.items().getFirst().errorCode());
        assertThrows(BlogException.class, () -> service.generate("story", new GenerateCommentBatchCommand(List.of("a", "a"))));
        assertThrows(BlogException.class, () -> service.generate("story", new GenerateCommentBatchCommand(List.of())));
    }

    @Test
    void analyticsUsesPublicScopeServerClockAndRetentionCutoff() {
        var saved = new ArrayList<ArticleVisit>();
        var cutoffs = new ArrayList<Instant>();
        var posts = fake(PostRepository.class, (method, args) -> {
            assertEquals(PostQueryScope.PUBLISHED, args[1]); return Optional.of(post(PostVisibility.PUBLIC));
        });
        var visits = fake(ArticleVisitRepository.class, (method, args) -> {
            if (method.equals("add")) saved.add((ArticleVisit) args[0]);
            else cutoffs.add((Instant) args[0]);
            return null;
        });
        var service = new VisitAnalyticsApplicationService(posts, visits, context(), TX, CLOCK, 90, true);
        service.record("story", new RecordArticleVisitCommand(UUID.randomUUID().toString(), "127.0.0.1", "ua", "", ""));
        assertEquals(NOW, saved.getFirst().visitedAt());
        assertEquals("post-id", saved.getFirst().postId());
        service.overview(new VisitAnalyticsQuery(7, 1, 25, null));
        assertEquals(Instant.parse("2026-08-30T16:00:00Z"), cutoffs.getFirst());
        service.purgeExpired();
        assertEquals(NOW.minus(Duration.ofDays(90)), cutoffs.getLast());
        assertThrows(BlogException.class, () -> service.overview(new VisitAnalyticsQuery(7, 0, 25, null)));
    }

    @Test
    void disabledAnalyticsDoesNotTouchPorts() {
        var posts = fake(PostRepository.class, (method, args) -> { throw new AssertionError("统计已关闭"); });
        var visits = fake(ArticleVisitRepository.class, (method, args) -> { throw new AssertionError("统计已关闭"); });
        new VisitAnalyticsApplicationService(posts, visits, context(), TX, CLOCK, 90, false)
                .record("story", new RecordArticleVisitCommand("unused", "", "", "", ""));
    }

    private static VisitorContextPort context() {
        return new VisitorContextPort() {
            public VisitorDevice device(String ua, String model) { return new VisitorDevice(DeviceType.UNKNOWN, "", "", ""); }
            public String location(String ip) { return "本地 / 内网"; }
            public String visitorKey(String ip, String ua) { return "key"; }
        };
    }
    private static AiGenerationTaskRunner sequentialRunner() {
        return new AiGenerationTaskRunner() {
            public <T> List<T> run(String key, Runnable prepare, List<Supplier<T>> tasks) {
                prepare.run(); return tasks.stream().map(Supplier::get).toList();
            }
        };
    }
    // 测试专用的轻量端口替身；没有引入第二套文章存储或生命周期实现。
    private interface Call { Object invoke(String method, Object[] args); }
    private static <T> T fake(Class<T> port, Call call) {
        return port.cast(Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[]{port},
                (proxy, method, args) -> call.invoke(method.getName(), args)));
    }
}
