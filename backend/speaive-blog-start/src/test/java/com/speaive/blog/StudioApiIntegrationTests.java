package com.speaive.blog;

import com.jayway.jsonpath.JsonPath;
import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.importing.MarkdownInboxUseCase;
import com.speaive.blog.application.port.in.automation.CommunityAutomationUseCase;
import com.speaive.blog.application.port.in.post.PostUseCase;
import com.speaive.blog.application.port.out.ai.AiCommentGeneration;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiCommentPrompt;
import com.speaive.blog.application.port.out.ai.AiSummaryGeneration;
import com.speaive.blog.application.port.out.ai.AiSummaryGenerationPort;
import com.speaive.blog.application.result.importing.MarkdownImportOutcome;
import com.speaive.blog.application.result.post.PostDetailResult;
import com.speaive.blog.interfaces.importing.MarkdownInboxImporter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StudioApiIntegrationTests {
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    private static final String PASSWORD = "speaive-test-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final Path DATA_DIRECTORY = createTempDirectory();
    private static final byte[] VALID_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("speaive_blog_test")
            .withUsername("speaive")
            .withPassword("speaive-test-db-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("speaive.content.data-directory", DATA_DIRECTORY::toString);
        registry.add("speaive.content.import-directory", () -> DATA_DIRECTORY.resolve("inbox").toString());
        registry.add("speaive.content.import-scan-interval", () -> "24h");
        registry.add("speaive.security.admin-username", () -> "admin");
        registry.add("speaive.security.admin-password-hash", () -> PASSWORD_HASH);
        registry.add("speaive.security.secure-cookies", () -> false);
    }

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final MarkdownInboxImporter inboxImporter;
    private final PostUseCase posts;
    private final MarkdownInboxUseCase inboxImports;
    private final CommunityAutomationUseCase communityAutomation;

    @MockitoBean
    AiCommentGenerationPort aiComments;

    @MockitoBean
    AiSummaryGenerationPort aiSummaries;

    @Autowired
    StudioApiIntegrationTests(
            MockMvc mockMvc,
            JdbcTemplate jdbc,
            MarkdownInboxImporter inboxImporter,
            PostUseCase posts,
            MarkdownInboxUseCase inboxImports,
            CommunityAutomationUseCase communityAutomation) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.inboxImporter = inboxImporter;
        this.posts = posts;
        this.inboxImports = inboxImports;
        this.communityAutomation = communityAutomation;
    }

    @BeforeEach
    void clearContent() throws Exception {
        jdbc.update("DELETE FROM blog_post_revision_tag");
        jdbc.update("DELETE FROM blog_post_revision");
        jdbc.update("DELETE FROM blog_markdown_import");
        jdbc.update("DELETE FROM blog_media");
        jdbc.update("DELETE FROM blog_post_tag");
        jdbc.update("DELETE FROM blog_post");
        jdbc.update("DELETE FROM blog_user WHERE id <> ?", ADMIN_ID);
        jdbc.update("DELETE FROM blog_invitation");
        clearDirectory(DATA_DIRECTORY);
        Files.createDirectories(DATA_DIRECTORY.resolve("inbox"));
        when(aiComments.isAvailable()).thenReturn(true);
        when(aiComments.generate(any())).thenReturn(
                new AiCommentGeneration("这篇文章把一个普通瞬间写得很具体，也留下了继续追问的空间。", "test-model", 12, 18));
        when(aiSummaries.isAvailable()).thenReturn(true);
        when(aiSummaries.generate(any())).thenReturn(
                new AiSummaryGeneration("文章记录了一个具体瞬间，并围绕它留下了值得继续讨论的问题。", "test-model", 20, 12));
    }

    @AfterAll
    static void removeTempDirectory() throws Exception {
        clearDirectory(DATA_DIRECTORY);
        Files.deleteIfExists(DATA_DIRECTORY);
    }

    @Test
    void csrfLoginSessionAndLogoutUseCookieSession() throws Exception {
        mockMvc.perform(get("/api/v1/studio/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/studio/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Client client = login();
        mockMvc.perform(get("/api/v1/studio/session").session(client.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));

        mockMvc.perform(post("/api/v1/studio/logout")
                        .session(client.session())
                        .cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));

        mockMvc.perform(get("/api/v1/studio/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inviteOnlyMembersCreateAgentsThatRequireAdminReviewAndAutoCommentsRemainPending() throws Exception {
        Client admin = login();
        MvcResult invitation = mockMvc.perform(post("/api/v1/studio/invitations")
                        .session(admin.session()).cookie(admin.csrfCookie())
                        .header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"validDays\":30,\"maxUses\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.startsWith("spv_")))
                .andReturn();
        String invitationCode = JsonPath.read(invitation.getResponse().getContentAsString(), "$.code");

        MvcResult anonymousCsrf = mockMvc.perform(get("/api/v1/account/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie anonymousCookie = anonymousCsrf.getResponse().getCookie("XSRF-TOKEN");
        String anonymousToken = JsonPath.read(anonymousCsrf.getResponse().getContentAsString(), "$.token");
        String anonymousHeader = JsonPath.read(anonymousCsrf.getResponse().getContentAsString(), "$.headerName");
        MvcResult registered = mockMvc.perform(post("/api/v1/account/register")
                        .cookie(anonymousCookie).header(anonymousHeader, anonymousToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"member-one",
                                  "displayName":"一号读者",
                                  "password":"member-password-123",
                                  "invitationCode":"%s"
                                }
                                """.formatted(invitationCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("member-one"))
                .andReturn();
        MockHttpSession memberSession = (MockHttpSession) registered.getRequest().getSession(false);
        Cookie memberCookie = registered.getResponse().getCookie("XSRF-TOKEN");
        MvcResult memberCsrf = mockMvc.perform(get("/api/v1/account/csrf")
                        .session(memberSession).cookie(memberCookie))
                .andExpect(status().isOk())
                .andReturn();
        String memberToken = JsonPath.read(memberCsrf.getResponse().getContentAsString(), "$.token");
        String memberHeader = JsonPath.read(memberCsrf.getResponse().getContentAsString(), "$.headerName");

        mockMvc.perform(get("/api/v1/studio/agents").session(memberSession))
                .andExpect(status().isForbidden());

        MvcResult createdAgent = mockMvc.perform(post("/api/v1/account/agents")
                        .session(memberSession).cookie(memberCookie).header(memberHeader, memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"member-critic",
                                  "displayName":"会员评论家",
                                  "avatarUrl":null,
                                  "systemPrompt":"阅读文章后给出具体、有边界的不同意见。",
                                  "temperature":0.7,
                                  "autoCommentEnabled":true,
                                  "autoCommentAllPosts":true,
                                  "autoCommentTags":[]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.canProcessPrivate").value(false))
                .andExpect(jsonPath("$.model").value(nullValue()))
                .andReturn();
        String agentId = JsonPath.read(createdAgent.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/studio/agents/" + agentId + "/approve")
                        .session(memberSession).cookie(memberCookie).header(memberHeader, memberToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isForbidden());

        MvcResult approved = mockMvc.perform(post("/api/v1/studio/agents/" + agentId + "/approve")
                        .session(admin.session()).cookie(admin.csrfCookie()).header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();
        int approvedVersion = JsonPath.read(approved.getResponse().getContentAsString(), "$.version");

        MvcResult changed = mockMvc.perform(put("/api/v1/account/agents/" + agentId)
                        .session(memberSession).cookie(memberCookie).header(memberHeader, memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"会员评论家",
                                  "avatarUrl":null,
                                  "systemPrompt":"修改后，必须重新审核才能继续评论。",
                                  "temperature":0.6,
                                  "version":%d
                                }
                                """.formatted(approvedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andReturn();
        int changedVersion = JsonPath.read(changed.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(post("/api/v1/studio/agents/" + agentId + "/approve")
                        .session(admin.session()).cookie(admin.csrfCookie()).header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + changedVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

        MvcResult createdPost = mockMvc.perform(post("/api/v1/studio/posts")
                        .session(admin.session()).cookie(admin.csrfCookie()).header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(createJson("community-post", "社区文章")))
                .andExpect(status().isCreated()).andReturn();
        String draftVersion = JsonPath.read(createdPost.getResponse().getContentAsString(), "$.version");
        mockMvc.perform(post("/api/v1/studio/posts/community-post/publish")
                        .session(admin.session()).cookie(admin.csrfCookie()).header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"" + draftVersion + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/studio/posts/community-post/community-agents")
                        .session(admin.session()).cookie(admin.csrfCookie()).header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.queuedAgents").value(1));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM blog_community_comment_job WHERE agent_id = ?", String.class, agentId))
                .isEqualTo("PENDING");

        var batch = communityAutomation.processDueJobs(3);
        assertThat(batch.succeeded()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM blog_community_comment_job WHERE agent_id = ?", String.class, agentId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM blog_comment WHERE author_id = ?", String.class, agentId))
                .isEqualTo("PENDING");
        mockMvc.perform(get("/api/v1/public/posts/community-post/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void updatesPreserveAgentAuthorsAndDisabledAuthorsRemainVisible() throws Exception {
        String agentId = "33333333-3333-3333-3333-333333333333";
        jdbc.update("""
                INSERT INTO blog_user (
                    id, username, display_name, type, status, avatar_url, created_at, updated_at
                ) VALUES (?, 'quiet-critic', '安静的批评家', 'AGENT', 'DISABLED',
                    '/media/agents/quiet-critic.png', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, agentId);
        PostDetailResult created = posts.createDraft(command("agent-authored", "Agent 原文"));
        jdbc.update("UPDATE blog_post SET author_id = ? WHERE slug = ?", agentId, created.slug());

        PostDetailResult updated = posts.update(
                created.slug(), created.version(), command(created.slug(), "Agent 修改"));
        PostDetailResult published = posts.publish(updated.slug(), updated.version());

        assertThat(published.author().id()).isEqualTo(agentId);
        assertThat(published.author().username()).isEqualTo("quiet-critic");
        assertThat(published.author().displayName()).isEqualTo("安静的批评家");
        assertThat(published.author().type()).isEqualTo("AGENT");
        assertThat(published.author().avatarUrl()).isEqualTo("/media/agents/quiet-critic.png");
        assertThat(jdbc.queryForList("""
                SELECT author_id
                FROM blog_post_revision
                WHERE slug = ? AND revision > 1
                ORDER BY revision
                """, String.class, created.slug())).containsExactly(agentId, agentId);

        mockMvc.perform(get("/api/v1/public/posts/agent-authored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.id").value(agentId))
                .andExpect(jsonPath("$.author.username").value("quiet-critic"))
                .andExpect(jsonPath("$.author.displayName").value("安静的批评家"))
                .andExpect(jsonPath("$.author.type").value("AGENT"))
                .andExpect(jsonPath("$.author.avatarUrl").value("/media/agents/quiet-critic.png"));
        mockMvc.perform(get("/api/v1/public/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].author.id").value(agentId));
    }

    @Test
    void clientSuppliedAuthorFieldsCannotOverrideServerAssignedAdmin() throws Exception {
        String agentId = "44444444-4444-4444-4444-444444444444";
        jdbc.update("""
                INSERT INTO blog_user (
                    id, username, display_name, type, status, avatar_url, created_at, updated_at
                ) VALUES (?, 'spoofed-agent', '伪造作者', 'AGENT', 'ACTIVE', NULL,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, agentId);
        Client client = login();
        String request = """
                {
                  "slug": "server-assigned-author",
                  "title": "服务端指定作者",
                  "description": "",
                  "publishedAt": "2026-08-02T08:00:00Z",
                  "tags": [],
                  "cover": null,
                  "body": "正文",
                  "authorId": "%s",
                  "author": {
                    "id": "%s",
                    "username": "spoofed-agent",
                    "displayName": "伪造作者",
                    "type": "AGENT",
                    "avatarUrl": null
                  }
                }
                """.formatted(agentId, agentId);

        mockMvc.perform(post("/api/v1/studio/posts")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(ADMIN_ID))
                .andExpect(jsonPath("$.author.username").value("admin"));
        assertThat(jdbc.queryForObject(
                "SELECT author_id FROM blog_post WHERE slug = ?",
                String.class,
                "server-assigned-author"))
                .isEqualTo(ADMIN_ID);
    }

    @Test
    void adminCreatesAgentGeneratesPendingCommentAndControlsPublicVisibility() throws Exception {
        Client client = login();
        MvcResult createdAgent = mockMvc.perform(post("/api/v1/studio/agents")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"empathetic-reader",
                                  "displayName":"共情读者",
                                  "avatarUrl":null,
                                  "systemPrompt":"阅读文章并给出具体、克制的共情回应。",
                                  "model":null,
                                  "temperature":0.7,
                                  "canProcessPrivate":false,
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("empathetic-reader"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();
        String agentId = JsonPath.read(createdAgent.getResponse().getContentAsString(), "$.id");
        MvcResult createdCritic = mockMvc.perform(post("/api/v1/studio/agents")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"skeptical-critic",
                                  "displayName":"挑剔评论家",
                                  "avatarUrl":null,
                                  "systemPrompt":"找出其他评论忽略的条件，直接但克制地提出反驳。",
                                  "model":null,
                                  "temperature":0.8,
                                  "canProcessPrivate":false,
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String criticId = JsonPath.read(createdCritic.getResponse().getContentAsString(), "$.id");

        PostDetailResult olderMemory = posts.createDraft(new PostWriteCommand(
                "older-memory", "更早的生活记录", "", java.time.Instant.parse("2025-01-02T08:00:00Z"),
                List.of("生活"), null, "PUBLIC", "一段更早的生活记录。"));
        posts.publish(olderMemory.slug(), olderMemory.version());
        PostDetailResult recentMemory = posts.createDraft(new PostWriteCommand(
                "recent-memory", "最近的生活记录", "", java.time.Instant.parse("2026-07-02T08:00:00Z"),
                List.of("生活", "随记"), null, "PUBLIC", "一段最近的生活记录。"));
        posts.publish(recentMemory.slug(), recentMemory.version());
        posts.createDraft(new PostWriteCommand(
                "private-memory", "不能泄露的生活记录", "", java.time.Instant.parse("2026-07-20T08:00:00Z"),
                List.of("生活", "随记"), null, "ADMIN_ONLY", "这段私密经历不能进入公开评论上下文。"));
        for (String relatedSlug : List.of("older-memory", "recent-memory", "private-memory")) {
            mockMvc.perform(post("/api/v1/studio/posts/" + relatedSlug + "/ai-summary")
                            .session(client.session()).cookie(client.csrfCookie())
                            .header(client.csrfHeader(), client.csrfToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("CURRENT"));
        }

        MvcResult createdPost = mockMvc.perform(post("/api/v1/studio/posts")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("ai-comment-flow", "AI 评论验收")))
                .andExpect(status().isCreated())
                .andReturn();
        String postVersion = JsonPath.read(createdPost.getResponse().getContentAsString(), "$.version");
        mockMvc.perform(post("/api/v1/studio/posts/ai-comment-flow/publish")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"" + postVersion + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/posts/ai-comment-flow/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        MvcResult generated = mockMvc.perform(post("/api/v1/studio/posts/ai-comment-flow/ai-comments")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.author.type").value("AGENT"))
                .andExpect(jsonPath("$.body").value(containsString("普通瞬间")))
                .andReturn();
        String commentId = JsonPath.read(generated.getResponse().getContentAsString(), "$.id");
        ArgumentCaptor<AiCommentPrompt> promptCaptor = ArgumentCaptor.forClass(AiCommentPrompt.class);
        verify(aiComments).generate(promptCaptor.capture());
        assertThat(promptCaptor.getValue().relatedPosts())
                .extracting(AiCommentPrompt.RelatedPost::title)
                .containsExactly("更早的生活记录", "最近的生活记录");

        mockMvc.perform(get("/api/v1/public/posts/ai-comment-flow/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/v1/studio/posts/ai-comment-flow/comments").session(client.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));

        MvcResult generatedReply = mockMvc.perform(post("/api/v1/studio/comments/" + commentId + "/ai-replies")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + criticId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.parentCommentId").value(commentId))
                .andExpect(jsonPath("$.author.displayName").value("挑剔评论家"))
                .andReturn();
        String replyId = JsonPath.read(generatedReply.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/studio/comments/" + replyId + "/publish")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/studio/comments/" + commentId + "/publish")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(post("/api/v1/studio/comments/" + replyId + "/publish")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/public/posts/ai-comment-flow/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].author.displayName").value("共情读者"))
                .andExpect(jsonPath("$.items[1].parentCommentId").value(commentId));

        mockMvc.perform(post("/api/v1/studio/comments/" + commentId + "/hide")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"));
        mockMvc.perform(get("/api/v1/public/posts/ai-comment-flow/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM blog_agent_run WHERE agent_id = ?", String.class, agentId))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_comment WHERE status = 'HIDDEN'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_post_ai_summary", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void privatePostRequiresExplicitAgentPermissionAndSuccessfulRunIsIdempotent() throws Exception {
        Client client = login();
        MvcResult createdAgent = mockMvc.perform(post("/api/v1/studio/agents")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"public-only-reader",
                                  "displayName":"公开文章读者",
                                  "avatarUrl":null,
                                  "systemPrompt":"只评论明确提供的公开文章。",
                                  "model":null,
                                  "temperature":0.4,
                                  "canProcessPrivate":false,
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String agentId = JsonPath.read(createdAgent.getResponse().getContentAsString(), "$.id");
        posts.createDraft(new PostWriteCommand(
                "private-agent-guard", "私密评论保护", "", java.time.Instant.parse("2026-08-02T08:00:00Z"),
                List.of(), null, "ADMIN_ONLY", "这是仅自己可见的正文"));

        mockMvc.perform(post("/api/v1/studio/posts/private-agent-guard/ai-comments")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_agent_run", Integer.class)).isZero();

        jdbc.update("UPDATE blog_agent SET can_process_private = TRUE WHERE id = ?", agentId);
        mockMvc.perform(post("/api/v1/studio/posts/private-agent-guard/ai-comments")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(post("/api/v1/studio/posts/private-agent-guard/ai-comments")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERATION_CONFLICT"));
    }

    @Test
    void everyWriteAdvancesRevisionAndArchiveKeepsHistoryWhileReleasingSlug() throws Exception {
        Client client = login();
        String createJson = createJson("writing-flow", "第一篇随记");
        MvcResult created = mockMvc.perform(post("/api/v1/studio/posts")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.author.id").value(ADMIN_ID))
                .andExpect(jsonPath("$.author.username").value("admin"))
                .andExpect(jsonPath("$.author.displayName").value("Speaive"))
                .andExpect(jsonPath("$.author.type").value("HUMAN"))
                .andExpect(jsonPath("$.author.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.version", endsWith(":1")))
                .andExpect(jsonPath("$.html", not(containsString("<script"))))
                .andReturn();
        String version = JsonPath.read(created.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(get("/api/v1/public/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/v1/public/posts/writing-flow"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/studio/posts")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_CONFLICT"));

        String updateJson = """
                {
                  "title":"第一篇随记（已保存）",
                  "description":"更新后的摘要",
                  "publishedAt":"2026-08-02T08:00:00Z",
                  "tags":["随记"],
                  "cover":null,
                  "visibility":"PUBLIC",
                  "body":"更新后的正文",
                  "version":"%s"
                }
                """.formatted(version);
        MvcResult updated = mockMvc.perform(put("/api/v1/studio/posts/writing-flow")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第一篇随记（已保存）"))
                .andExpect(jsonPath("$.author.id").value(ADMIN_ID))
                .andExpect(jsonPath("$.version", endsWith(":2")))
                .andReturn();
        String updatedVersion = JsonPath.read(updated.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(put("/api/v1/studio/posts/writing-flow")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        MvcResult published = mockMvc.perform(post("/api/v1/studio/posts/writing-flow/publish")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"" + updatedVersion + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version", endsWith(":3")))
                .andReturn();
        String publishedVersion = JsonPath.read(published.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(get("/api/v1/public/posts/writing-flow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第一篇随记（已保存）"))
                .andExpect(jsonPath("$.author.id").value(ADMIN_ID));

        mockMvc.perform(get("/api/v1/public/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].author.id").value(ADMIN_ID))
                .andExpect(jsonPath("$.items[0].author.username").value("admin"));

        mockMvc.perform(get("/api/v1/studio/posts").session(client.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].author.id").value(ADMIN_ID))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        MvcResult unpublished = mockMvc.perform(post("/api/v1/studio/posts/writing-flow/unpublish")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"" + publishedVersion + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version", endsWith(":4")))
                .andReturn();
        String unpublishedVersion = JsonPath.read(unpublished.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(post("/api/v1/studio/posts/writing-flow/archive")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/studio/posts/writing-flow/archive")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"" + unpublishedVersion + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_post", Integer.class)).isZero();
        assertThat(jdbc.queryForList(
                "SELECT revision FROM blog_post_revision WHERE slug = ? ORDER BY revision",
                Long.class, "writing-flow")).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(jdbc.queryForList(
                "SELECT event_type FROM blog_post_revision WHERE slug = ? ORDER BY revision",
                String.class, "writing-flow"))
                .containsExactly("CREATE", "UPDATE", "PUBLISH", "UNPUBLISH", "ARCHIVE");
        assertThat(jdbc.queryForList(
                "SELECT author_id FROM blog_post_revision WHERE slug = ? ORDER BY revision",
                String.class, "writing-flow"))
                .containsOnly(ADMIN_ID);

        mockMvc.perform(post("/api/v1/studio/posts")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", endsWith(":1")));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_post_revision WHERE slug = ?", Integer.class, "writing-flow"))
                .isEqualTo(6);
    }

    @Test
    void markdownIsSanitizedInPreview() throws Exception {
        Client client = login();
        String body = """
                {"body":"[危险](javascript:alert(1))\\n\\n<img src=\\"/media/2026/08/a.png\\" onerror=\\"alert(1)\\">\\n\\n<script>alert(2)</script>"}
                """;
        mockMvc.perform(post("/api/v1/studio/preview")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html", not(containsString("javascript:"))))
                .andExpect(jsonPath("$.html", not(containsString("onerror"))))
                .andExpect(jsonPath("$.html", not(containsString("<script"))))
                .andExpect(jsonPath("$.html", containsString("/media/2026/08/a.png")));
    }

    @Test
    void markdownEndpointsRequireAuthenticationAndCsrf() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/studio/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String csrfJson = csrf.getResponse().getContentAsString();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String csrfHeader = JsonPath.read(csrfJson, "$.headerName");
        String csrfToken = JsonPath.read(csrfJson, "$.token");

        mockMvc.perform(post("/api/v1/studio/preview")
                        .cookie(csrfCookie)
                        .header(csrfHeader, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"preview\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        MockMultipartFile anonymousMarkdown = new MockMultipartFile(
                "markdown", "anonymous.md", "text/markdown", "# Anonymous".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/studio/import")
                        .file(anonymousMarkdown)
                        .cookie(csrfCookie)
                        .header(csrfHeader, csrfToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        Client client = login();
        mockMvc.perform(post("/api/v1/studio/preview")
                        .session(client.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"preview\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MockMultipartFile csrfMarkdown = new MockMultipartFile(
                "markdown", "csrf.md", "text/markdown", "# CSRF".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/studio/import")
                        .file(csrfMarkdown)
                        .session(client.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void markdownEndpointsPreserveValidationAndFileNamePrecedence() throws Exception {
        Client client = login();

        mockMvc.perform(post("/api/v1/studio/preview")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("正文不能为空"));

        mockMvc.perform(post("/api/v1/studio/preview")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value(""));

        MockMultipartFile unnamedOversized = new MockMultipartFile(
                "markdown", "", "text/markdown", new byte[1_048_577]);
        mockMvc.perform(multipart("/api/v1/studio/import")
                        .file(unnamedOversized)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("上传文件缺少文件名"));
    }

    @Test
    void inboxImportsMarkdownRejectsSymlinksAndDeduplicatesBySha256() throws Exception {
        Path inbox = DATA_DIRECTORY.resolve("inbox");
        byte[] markdown = "# 目录直投文章\n\n这是一段正文。\n".getBytes(StandardCharsets.UTF_8);
        Files.write(inbox.resolve("direct-note.md"), markdown);
        Files.write(inbox.resolve("draft.md.uploading"), markdown);
        Files.write(inbox.resolve("draft.tmp"), markdown);
        Files.write(inbox.resolve(".hidden.md"), markdown);
        Path outside = Files.createTempFile("speaive-outside-", ".md");
        Files.writeString(outside, "# 不应读取\n", StandardCharsets.UTF_8);
        Files.createSymbolicLink(inbox.resolve("linked-note.md"), outside);

        inboxImporter.scanNow();

        Client client = login();
        mockMvc.perform(get("/api/v1/studio/posts/direct-note").session(client.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("目录直投文章"))
                .andExpect(jsonPath("$.body").value("这是一段正文。"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.author.id").value(ADMIN_ID))
                .andExpect(jsonPath("$.version", endsWith(":1")));

        assertThat(inbox.resolve("imported/direct-note.md")).isRegularFile();
        assertThat(inbox.resolve("draft.md.uploading")).isRegularFile();
        assertThat(inbox.resolve("draft.tmp")).isRegularFile();
        assertThat(inbox.resolve(".hidden.md")).isRegularFile();
        assertThat(inbox.resolve("rejected/linked-note.md.reason.txt"))
                .content(StandardCharsets.UTF_8).contains("符号链接");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_markdown_import", Integer.class)).isEqualTo(1);

        Files.write(inbox.resolve("direct-note.md"), markdown);
        inboxImporter.scanNow();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_post", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_markdown_import", Integer.class)).isEqualTo(1);
        try (var imported = Files.list(inbox.resolve("imported"))) {
            assertThat(imported.filter(path -> path.getFileName().toString().endsWith(".md")).count()).isEqualTo(2);
        }
        Files.deleteIfExists(outside);
    }

    @Test
    void inboxKeepsFilesForRetryWhenInfrastructureFails() throws Exception {
        Path retryInbox = DATA_DIRECTORY.resolve("retry-inbox");
        Files.createDirectories(retryInbox);
        Files.writeString(retryInbox.resolve("retry.md"), "# 稍后重试\n", StandardCharsets.UTF_8);
        MarkdownInboxUseCase failingImports = mock(MarkdownInboxUseCase.class);
        when(failingImports.importOnce(eq("retry.md"), any(byte[].class)))
                .thenThrow(new IllegalStateException("database temporarily unavailable"));
        MarkdownInboxImporter importer = new MarkdownInboxImporter(
                retryInbox, 1_048_576, failingImports);

        importer.scanNow();

        assertThat(retryInbox.resolve("retry.md")).isRegularFile();
        try (var rejected = Files.list(retryInbox.resolve("rejected"))) {
            assertThat(rejected).isEmpty();
        }
    }

    @Test
    void concurrentInboxImportsAreSerializedByContentHash() throws Exception {
        byte[] markdown = "# 并发导入\n\n正文。\n".getBytes(StandardCharsets.UTF_8);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger imported = new AtomicInteger();
        AtomicInteger deduplicated = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = List.of(0, 1).stream().map(index -> executor.submit(() -> {
                ready.countDown();
                start.await();
                MarkdownImportOutcome outcome = inboxImports.importOnce("concurrent-import.md", markdown);
                if (outcome == MarkdownImportOutcome.IMPORTED) {
                    imported.incrementAndGet();
                } else if (outcome == MarkdownImportOutcome.ALREADY_IMPORTED) {
                    deduplicated.incrementAndGet();
                }
                return null;
            })).toList();
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(imported).hasValue(1);
        assertThat(deduplicated).hasValue(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_post WHERE slug = ?", Integer.class, "concurrent-import"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_markdown_import", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void validatesMarkdownAndImageUploadsAndDoesNotExposeUnregisteredFiles() throws Exception {
        Client client = login();
        MockMultipartFile mdx = new MockMultipartFile("markdown", "component.mdx", "text/markdown",
                "# no mdx".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/studio/import").file(mdx)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_NAME"));

        byte[] tooLarge = new byte[1_048_577];
        MockMultipartFile oversized = new MockMultipartFile("markdown", "large.md", "text/markdown", tooLarge);
        mockMvc.perform(multipart("/api/v1/studio/import").file(oversized)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("TOO_LARGE"));

        MockMultipartFile markdown = new MockMultipartFile("markdown", "uploaded-note.md", "text/markdown",
                "# 上传的文章\n\n上传正文。".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/studio/import").file(markdown)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("uploaded-note"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.visibility").value("ADMIN_ONLY"))
                .andExpect(jsonPath("$.author.id").value(ADMIN_ID))
                .andExpect(jsonPath("$.body").value("上传正文。"))
                .andExpect(jsonPath("$.html", containsString("<p>上传正文。</p>")))
                .andExpect(jsonPath("$.version", endsWith(":1")));

        byte[] signatureOnly = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile incompleteImage = new MockMultipartFile("image", "incomplete.png", "image/png",
                signatureOnly);
        mockMvc.perform(multipart("/api/v1/studio/media").file(incompleteImage)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        byte[] png = VALID_PNG;
        MockMultipartFile wrongExtension = new MockMultipartFile("image", "photo.jpg", "image/jpeg", png);
        mockMvc.perform(multipart("/api/v1/studio/media").file(wrongExtension)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        MockMultipartFile validImage = new MockMultipartFile("image", "photo.png", "image/png", png);
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/studio/media").file(validImage)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.size").value(png.length))
                .andReturn();
        String url = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.url");

        mockMvc.perform(get(url))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/studio" + url).session(client.session()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_media", Integer.class)).isEqualTo(1);

        byte[] replaced = png.clone();
        replaced[replaced.length - 1] ^= 1;
        Files.write(DATA_DIRECTORY.resolve(url.substring(1)), replaced);
        mockMvc.perform(get("/api/v1/studio" + url).session(client.session()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"));

        Files.delete(DATA_DIRECTORY.resolve(url.substring(1)));
        mockMvc.perform(get("/api/v1/studio" + url).session(client.session()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"));

        Path unregistered = DATA_DIRECTORY.resolve("media/2026/08/unregistered.png");
        Files.createDirectories(unregistered.getParent());
        Files.write(unregistered, png);
        mockMvc.perform(get("/media/2026/08/unregistered.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void mediaUploadCompensatesTheFileWhenDatabaseInsertFails() throws Exception {
        Client client = login();
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION reject_blog_media_insert() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced media insert failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_blog_media_insert_trigger
                BEFORE INSERT ON blog_media
                FOR EACH ROW EXECUTE FUNCTION reject_blog_media_insert()
                """);
        try {
            MockMultipartFile image = new MockMultipartFile(
                    "image", "rollback.png", "image/png", VALID_PNG);
            mockMvc.perform(multipart("/api/v1/studio/media").file(image)
                            .session(client.session()).cookie(client.csrfCookie())
                            .header(client.csrfHeader(), client.csrfToken()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("STORAGE_ERROR"));

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_media", Integer.class)).isZero();
            try (var files = Files.walk(DATA_DIRECTORY.resolve("media"))) {
                assertThat(files.filter(Files::isRegularFile).count()).isZero();
            }
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_blog_media_insert_trigger ON blog_media");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_blog_media_insert()");
        }
    }

    @Test
    void adminOnlyPostsAndTheirMediaNeverLeakThroughPublicEndpoints() throws Exception {
        Client client = login();
        MockMultipartFile image = new MockMultipartFile("image", "secret.png", "image/png", VALID_PNG);
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/studio/media").file(image)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isCreated())
                .andReturn();
        String mediaUrl = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.url");
        String studioMediaUrl = "/api/v1/studio" + mediaUrl;

        String createPrivate = """
                {
                  "slug":"secret-note",
                  "title":"只有我能看的秘密",
                  "description":"私密摘要",
                  "publishedAt":"2026-08-02T08:00:00Z",
                  "tags":["秘密"],
                  "cover":"%s",
                  "visibility":"ADMIN_ONLY",
                  "body":"![秘密图片](%s)\\n\\n不对外公开。"
                }
                """.formatted(mediaUrl, mediaUrl);
        MvcResult created = mockMvc.perform(post("/api/v1/studio/posts")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(createPrivate))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("ADMIN_ONLY"))
                .andReturn();
        String createdVersion = JsonPath.read(created.getResponse().getContentAsString(), "$.version");

        MvcResult publishedPrivate = mockMvc.perform(post("/api/v1/studio/posts/secret-note/publish")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"" + createdVersion + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.visibility").value("ADMIN_ONLY"))
                .andReturn();
        String privateVersion = JsonPath.read(publishedPrivate.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(get("/api/v1/public/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/v1/public/posts/secret-note"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(mediaUrl))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(studioMediaUrl).session(client.session()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(VALID_PNG));

        String makePublic = """
                {
                  "title":"只有我能看的秘密",
                  "description":"私密摘要",
                  "publishedAt":"2026-08-02T08:00:00Z",
                  "tags":["秘密"],
                  "cover":"%s",
                  "visibility":"PUBLIC",
                  "body":"![秘密图片](%s)\\n\\n现在公开。",
                  "version":"%s"
                }
                """.formatted(mediaUrl, mediaUrl, privateVersion);
        MvcResult publicPost = mockMvc.perform(put("/api/v1/studio/posts/secret-note")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(makePublic))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andReturn();
        String publicVersion = JsonPath.read(publicPost.getResponse().getContentAsString(), "$.version");

        mockMvc.perform(get("/api/v1/public/posts/secret-note"))
                .andExpect(status().isOk());
        mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(VALID_PNG));

        String makePrivateAgain = makePublic
                .replace("\"visibility\":\"PUBLIC\"", "\"visibility\":\"ADMIN_ONLY\"")
                .replace("\"version\":\"" + privateVersion + "\"", "\"version\":\"" + publicVersion + "\"");
        mockMvc.perform(put("/api/v1/studio/posts/secret-note")
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content(makePrivateAgain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("ADMIN_ONLY"));

        mockMvc.perform(get("/api/v1/public/posts/secret-note"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(mediaUrl))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_post_media WHERE relative_path = ?", Integer.class,
                mediaUrl.substring("/media/".length()))).isEqualTo(1);
    }

    @Test
    void concurrentUpdatesUseDatabaseRevisionCas() throws Exception {
        String version = posts.createDraft(command("concurrent-post", "并发文章")).version();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = List.of(0, 1).stream().map(index -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    posts.update("concurrent-post", version,
                            command("concurrent-post", "并发修改 " + index));
                    updated.incrementAndGet();
                } catch (BlogException exception) {
                    if (exception.code() != BlogErrorCode.VERSION_CONFLICT) {
                        throw exception;
                    }
                    conflicted.incrementAndGet();
                }
                return null;
            })).toList();
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(updated).hasValue(1);
        assertThat(conflicted).hasValue(1);
        assertThat(jdbc.queryForObject(
                "SELECT revision FROM blog_post WHERE slug = ?", Long.class, "concurrent-post"))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_post_revision WHERE slug = ?", Integer.class, "concurrent-post"))
                .isEqualTo(2);
    }

    @Test
    void staleEditorCannotOverwriteRecreatedPostWithTheSameSlug() {
        String oldVersion = posts.createDraft(command("reused-slug", "旧文章")).version();
        posts.archive("reused-slug", oldVersion);
        String newVersion = posts.createDraft(command("reused-slug", "新文章")).version();

        assertThat(newVersion).isNotEqualTo(oldVersion).endsWith(":1");
        assertThatThrownBy(() -> posts.update(
                "reused-slug", oldVersion, command("reused-slug", "旧页面误保存")))
                .isInstanceOfSatisfying(BlogException.class,
                        exception -> assertThat(exception.code()).isEqualTo(BlogErrorCode.VERSION_CONFLICT));
        PostDetailResult recreated = posts.getStudioPost("reused-slug");
        assertThat(recreated.title()).isEqualTo("新文章");
        assertThat(recreated.version()).isEqualTo(newVersion);
    }

    @Test
    void concurrentCreatesRelyOnDatabaseSlugUniqueness() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = List.of(0, 1).stream().map(index -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    posts.createDraft(command("same-slug", "并发创建 " + index));
                    created.incrementAndGet();
                } catch (BlogException exception) {
                    if (exception.code() != BlogErrorCode.SLUG_CONFLICT) {
                        throw exception;
                    }
                    conflicted.incrementAndGet();
                }
                return null;
            })).toList();
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(created).hasValue(1);
        assertThat(conflicted).hasValue(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_post WHERE slug = ?", Integer.class, "same-slug"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blog_post_revision WHERE slug = ?", Integer.class, "same-slug"))
                .isEqualTo(1);
    }

    @Test
    void archiveRollsBackSnapshotAndRevisionWhenDeleteFails() {
        String version = posts.createDraft(command("rollback-archive", "归档事务")).version();
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION reject_blog_post_delete() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced archive delete failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_blog_post_delete_trigger
                BEFORE DELETE ON blog_post
                FOR EACH ROW EXECUTE FUNCTION reject_blog_post_delete()
        """);
        try {
            assertThatThrownBy(() -> posts.archive("rollback-archive", version))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT revision FROM blog_post WHERE slug = ?", Long.class, "rollback-archive"))
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM blog_post_revision WHERE slug = ?", Integer.class, "rollback-archive"))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS reject_blog_post_delete_trigger ON blog_post");
            jdbc.execute("DROP FUNCTION IF EXISTS reject_blog_post_delete()");
        }
    }

    private Client login() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/studio/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String csrfJson = csrf.getResponse().getContentAsString();
        String csrfToken = JsonPath.read(csrfJson, "$.token");
        String csrfHeader = JsonPath.read(csrfJson, "$.headerName");
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");

        MvcResult login = mockMvc.perform(post("/api/v1/studio/login")
                        .cookie(csrfCookie)
                        .header(csrfHeader, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        Cookie rotatedCookie = login.getResponse().getCookie("XSRF-TOKEN");
        assertThat(rotatedCookie.getValue()).isNotEqualTo(csrfCookie.getValue());

        MvcResult refreshedCsrf = mockMvc.perform(get("/api/v1/studio/csrf")
                        .session(session)
                        .cookie(rotatedCookie))
                .andExpect(status().isOk())
                .andReturn();
        String refreshedJson = refreshedCsrf.getResponse().getContentAsString();
        return new Client(session, rotatedCookie,
                JsonPath.read(refreshedJson, "$.headerName"),
                JsonPath.read(refreshedJson, "$.token"));
    }

    private static String createJson(String slug, String title) {
        return """
                {
                  "slug": "%s",
                  "title": "%s",
                  "description": "",
                  "publishedAt": "2026-08-02T08:00:00Z",
                  "tags": ["随记", "生活"],
                  "cover": null,
                  "visibility": "PUBLIC",
                  "body": "## 正文\\n\\n**hello**\\n\\n<script>alert(1)</script>"
                }
                """.formatted(slug, title);
    }

    private static PostWriteCommand command(String slug, String title) {
        return new PostWriteCommand(slug, title, "", java.time.Instant.parse("2026-08-02T08:00:00Z"),
                List.of("测试"), null, "PUBLIC", "正文");
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("speaive-blog-db-test-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void clearDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private record Client(MockHttpSession session, Cookie csrfCookie, String csrfHeader, String csrfToken) {
    }
}
