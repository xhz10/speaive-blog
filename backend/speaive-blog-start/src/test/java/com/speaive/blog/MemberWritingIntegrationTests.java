package com.speaive.blog;

import com.jayway.jsonpath.JsonPath;
import com.speaive.blog.application.command.account.ContentEncryptionCommand;
import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.port.in.account.WritingAccountUseCase;
import com.speaive.blog.application.port.in.post.MemberWritingUseCase;
import com.speaive.blog.application.port.out.persistence.AccountRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.post.PostDetailResult;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 PostgreSQL、会话和 CSRF 验证：权限隔离、完整历史加密、CAS 与原子转换。 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class MemberWritingIntegrationTests {
    static final String PASSWORD = "member-test-password";
    static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    static final Path DIRECTORY = directory();
    static final Path KEY_FILE = keyFile();
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("member_writing_test").withUsername("speaive").withPassword("test-db-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("speaive.security.admin-username", () -> "admin");
        registry.add("speaive.security.admin-password-hash", () -> HASH);
        registry.add("speaive.content.data-directory", () -> DIRECTORY.resolve("data").toString());
        registry.add("speaive.content.encryption-key-file", KEY_FILE::toString);
        registry.add("speaive.content.import-enabled", () -> false);
        registry.add("speaive.ai.community-automation-enabled", () -> false);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberWritingUseCase writing;
    @Autowired WritingAccountUseCase settings;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRunner transactions;

    @BeforeEach void clear() {
        jdbc.update("DELETE FROM blog_user WHERE id IN (SELECT id FROM blog_account)");
        jdbc.update("DELETE FROM blog_invitation");
    }
    @AfterAll static void cleanup() throws Exception {
        try (var files = Files.walk(DIRECTORY)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
    }

    @Test
    void registrationDefaultsToReaderAndOnlyAdminCanGrantWritingRights() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice");
        mvc.perform(get("/api/v1/account/writing/settings").session(alice.session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("READER"))
                .andExpect(jsonPath("$.contentEncrypted").value(false));
        send(alice, post("/api/v1/account/writing/posts"), content(null)).andExpect(status().isForbidden());
        send(alice, put("/api/v1/studio/members/alice/permissions"), permissions(true, true, 1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/studio/members").session(alice.session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/account/writing/posts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/account/writing/posts").session(alice.session).contentType(MediaType.APPLICATION_JSON).content(content(null)))
                .andExpect(status().isForbidden());
        send(alice, put("/api/v1/account/writing/settings/encryption"), "{\"encrypted\":true,\"version\":1}").andExpect(status().isBadRequest());
        grant(admin, "alice", false);
        var created = create(alice);
        assertThat(read(created, "$.author.username")).isEqualTo("alice");
        send(alice, post("/api/v1/account/writing/posts/" + read(created, "$.slug") + "/publish"), version(created)).andExpect(status().isForbidden());
        send(admin, put("/api/v1/studio/members/alice/permissions"), permissions(true, true, 1)).andExpect(status().isConflict());
    }

    @Test
    void ownershipAppliesToEveryEndpointAndEncryptionDoesNotChangePublicVisibility() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice"); Client bob = register(admin, "bobby");
        grant(admin, "alice", true); grant(admin, "bobby", true);
        String created = create(alice); String slug = read(created, "$.slug");
        assertThat(slug).doesNotContain("私密");
        for (String suffix : new String[] { "", "/history" }) {
            mvc.perform(get("/api/v1/account/writing/posts/" + slug + suffix).session(bob.session)).andExpect(status().isNotFound());
        }
        send(bob, put("/api/v1/account/writing/posts/" + slug), content(read(created, "$.version"))).andExpect(status().isNotFound());
        for (String action : new String[] { "publish", "unpublish", "archive", "restore" }) {
            send(bob, post("/api/v1/account/writing/posts/" + slug + "/" + action), "{\"version\":\"" + read(created,"$.version") + "\",\"revision\":1}")
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/public/profiles/alice")).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/v1/public/profiles/alice/posts/" + slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/studio/posts/" + slug).session(admin.session)).andExpect(status().isNotFound());
        send(alice, put("/api/v1/account/writing/settings/encryption"), "{\"encrypted\":true,\"version\":2}").andExpect(status().isOk());
        assertEncrypted("alice");
        mvc.perform(get("/api/v1/account/writing/posts/" + slug).session(alice.session)).andExpect(jsonPath("$.title").value("私密标题"))
                .andExpect(jsonPath("$.body").value("这是一段秘密正文"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        String published = send(alice, post("/api/v1/account/writing/posts/" + slug + "/publish"), version(created))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/public/profiles/alice/posts/" + slug)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("私密标题"));
        mvc.perform(get("/api/v1/public/profiles/alice")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/public/posts/" + slug)).andExpect(status().isNotFound());
        send(admin, put("/api/v1/studio/members/alice/permissions"), permissions(false, true, 3)).andExpect(status().isOk());
        send(alice, put("/api/v1/account/writing/posts/" + slug), content(read(published, "$.version"))).andExpect(status().isForbidden());
        send(alice, post("/api/v1/account/writing/posts/" + slug + "/unpublish"), version(published)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/profiles/alice/posts/" + slug)).andExpect(status().isNotFound());
        assertEncrypted("alice");
    }

    @Test
    void protectionMigratesAllHistoryIncludingArchivedRowsAndPreservesCasTokens() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice"); grant(admin, "alice", true);
        PostDetailResult current = writing.create("alice", command("初始秘密"));
        String firstVersion = current.version();
        for (int index = 0; index < 104; index++) current = writing.update("alice", current.slug(), current.version(), command("秘密正文" + index));
        PostDetailResult archived = writing.create("alice", command("归档秘密"));
        writing.archive("alice", archived.slug(), archived.version());
        assertThat(writing.history("alice", current.slug()).items()).hasSize(50);
        settings.setEncryption("alice", new ContentEncryptionCommand(true, 2));
        assertEncrypted("alice");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_member_post_revision WHERE payload_encrypted", Integer.class)).isEqualTo(107);
        assertThat(writing.getOwn("alice", current.slug()).version()).isEqualTo(current.version());
        String slug = current.slug();
        assertThatThrownBy(() -> writing.update("alice", slug, firstVersion, command("过期覆盖"))).hasMessageContaining("刷新");
        PostDetailResult restored = writing.restore("alice", slug, 1, current.version());
        assertThat(restored.body()).isEqualTo("初始秘密");
        assertEncrypted("alice");
        settings.setEncryption("alice", new ContentEncryptionCommand(false, 3));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_member_post_revision WHERE payload_encrypted", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT payload FROM blog_member_post_revision WHERE archived", String.class)).allSatisfy(value -> assertThat(value).contains("归档秘密"));
        assertThat(writing.getOwn("alice", slug).version()).isEqualTo(restored.version());
    }

    @Test
    void damagedCiphertextFailsClosedAndRollingBackProtectionLeavesSettingsUntouched() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice"); grant(admin, "alice", true);
        String created = create(alice); settings.setEncryption("alice", new ContentEncryptionCommand(true, 2));
        String slug = read(created, "$.slug");
        String original = jdbc.queryForObject("SELECT payload FROM blog_member_post_revision WHERE slug = ?", String.class, slug);
        jdbc.update("UPDATE blog_member_post_revision SET payload = 'v1.test.broken.broken' WHERE slug = ?", slug);
        send(alice, put("/api/v1/account/writing/settings/encryption"), "{\"encrypted\":false,\"version\":3}")
                .andExpect(status().isInternalServerError()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("秘密正文"))));
        assertThat(settings.get("alice").contentEncrypted()).isTrue();
        assertThat(jdbc.queryForObject("SELECT payload_encrypted FROM blog_member_post WHERE slug = ?", Boolean.class, slug)).isTrue();
        jdbc.update("UPDATE blog_member_post_revision SET payload = ? WHERE slug = ?", original, slug);
        jdbc.update("UPDATE blog_member_post SET status = 'PUBLISHED' WHERE slug = ?", slug);
        mvc.perform(get("/api/v1/public/profiles/alice/posts/" + slug)).andExpect(status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("秘密正文"))));
        send(admin, put("/api/v1/studio/members/alice/permissions"), permissions(true, false, 3)).andExpect(status().isBadRequest());
    }

    @Test
    void accountRowLockSerializesEncryptionWithConcurrentWrites() throws Exception {
        Client admin = loginAdmin(); register(admin, "alice"); grant(admin, "alice", true);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> toggle = executor.submit(() -> transactions.required(() -> {
                accounts.lockByUsername("alice").orElseThrow(); locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("未释放测试锁"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return settings.setEncryption("alice", new ContentEncryptionCommand(true, 2));
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<PostDetailResult> create = executor.submit(() -> writing.create("alice", command("并发秘密")));
            assertThatThrownBy(() -> create.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); toggle.get(5, TimeUnit.SECONDS); create.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertEncrypted("alice");
    }

    @Test
    void rejectsUnownedMediaAndDoesNotSendPrivateContentToLegacyTables() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice"); grant(admin, "alice", true);
        assertThatThrownBy(() -> writing.create("alice", command("![图片](/media/private.png)"))).hasMessageContaining("站内媒体");
        settings.setEncryption("alice", new ContentEncryptionCommand(true, 2)); create(alice);
        for (String table : List.of("blog_post", "blog_post_revision", "blog_post_ai_summary", "blog_inspiration", "blog_editorial_review")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isZero();
        }
    }

    @Test
    void revokingWriterRoleWithdrawsPostsSoRegrantingDoesNotRepublishOldContent() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice"); grant(admin, "alice", true);
        String created = create(alice); String slug = read(created, "$.slug");
        send(alice, post("/api/v1/account/writing/posts/" + slug + "/publish"), version(created)).andExpect(status().isOk());
        settings.setEncryption("alice", new ContentEncryptionCommand(true, 2));
        send(admin, put("/api/v1/studio/members/alice/permissions"), permissions(false, true, 3).replace("WRITER", "READER"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/profiles/alice")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/account/writing/posts/" + slug).session(alice.session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        send(admin, put("/api/v1/studio/members/alice/permissions"), permissions(true, true, 4)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/profiles/alice")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        assertEncrypted("alice");
    }

    @Test
    void previewRendersUnsavedInputWithoutCreatingOrUpdatingAnyArticle() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice");
        String input = "{\"title\":\"尚未保存\",\"body\":\"## 预览\\n\\n<script>alert(1)</script>\\n正文\"}";
        send(alice, post("/api/v1/account/writing/preview"), input).andExpect(status().isForbidden());
        grant(admin, "alice", true);
        mvc.perform(post("/api/v1/account/writing/preview").session(alice.session)
                .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isForbidden());
        String preview = send(alice, post("/api/v1/account/writing/preview"), input)
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.title").value("尚未保存")).andReturn().getResponse().getContentAsString();
        assertThat(read(preview, "$.html")).contains("<h2>", "正文").doesNotContain("<script>");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_member_post", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_member_post_revision", Integer.class)).isZero();
        String existing = create(alice);
        send(alice, post("/api/v1/account/writing/preview"), input).andExpect(status().isOk());
        mvc.perform(get("/api/v1/account/writing/posts/" + read(existing, "$.slug")).session(alice.session))
                .andExpect(jsonPath("$.version").value(read(existing, "$.version")))
                .andExpect(jsonPath("$.body").value("这是一段秘密正文"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blog_member_post_revision", Integer.class)).isEqualTo(1);
    }

    @Test
    void archiveRecoveryPreservesHistoryEncryptionAndOwnershipAndCannotRepublish() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice"); Client bob = register(admin, "bobby");
        grant(admin, "alice", true); grant(admin, "bobby", true);
        String created = create(alice); String slug = read(created, "$.slug");
        var published = writing.publish("alice", slug, read(created, "$.version"));
        writing.archive("alice", slug, published.version());
        settings.setEncryption("alice", new ContentEncryptionCommand(true, 2));
        String token = writing.listOwn("alice", 1, "ARCHIVED").items().getFirst().version();
        assertThat(writing.listOwn("alice", 1, "ALL").total()).isZero();
        mvc.perform(get("/api/v1/account/writing/posts?filter=ARCHIVED").session(bob.session))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/account/writing/posts?filter=bad").session(alice.session)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/account/writing/posts/" + slug + "/recover").session(alice.session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"" + token + "\"}"))
                .andExpect(status().isForbidden());
        send(bob, post("/api/v1/account/writing/posts/" + slug + "/recover"), "{\"version\":\"" + token + "\"}")
                .andExpect(status().isNotFound());
        send(alice, post("/api/v1/account/writing/posts/" + slug + "/recover"), version(created)).andExpect(status().isConflict());
        send(alice, post("/api/v1/account/writing/posts/" + slug + "/recover"), "{\"version\":\"" + token + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.visibility").value("ADMIN_ONLY")).andExpect(jsonPath("$.body").value("这是一段秘密正文"));
        assertThat(writing.listOwn("alice", 1, "ARCHIVED").total()).isZero();
        assertThat(writing.listOwn("alice", 1, "PRIVATE").total()).isEqualTo(1);
        assertThat(writing.history("alice", slug).items()).hasSize(4);
        mvc.perform(get("/api/v1/public/profiles/alice/posts/" + slug)).andExpect(status().isNotFound());
        send(alice, post("/api/v1/account/writing/posts/" + slug + "/recover"), "{\"version\":\"" + token + "\"}")
                .andExpect(status().isNotFound());
        assertEncrypted("alice");
        var recovered = writing.getOwn("alice", slug); writing.archive("alice", slug, recovered.version());
        assertThat(writing.listOwn("alice", 1, "ARCHIVED").total()).isEqualTo(1);
        assertThat(writing.listOwn("alice", 1, "ARCHIVED").items()).hasSize(1);
    }

    @Test
    void communityFiltersBeforeDecryptionAndPaginatesOnlyPublicActiveAuthors() throws Exception {
        Client admin = loginAdmin(); register(admin, "alice"); register(admin, "bobby");
        grant(admin, "alice", true); grant(admin, "bobby", true);
        settings.setEncryption("alice", new ContentEncryptionCommand(true, 2));
        for (int n = 0; n < 21; n++) {
            var p = writing.create("alice", new PostWriteCommand(null, "公开" + n, "摘要", null, List.of(), null, "PUBLIC", "公开正文"));
            writing.publish("alice", p.slug(), p.version());
        }
        var privatePost = writing.create("alice", command("不能解密的私密正文"));
        jdbc.update("UPDATE blog_member_post SET payload = 'broken' WHERE slug = ?", privatePost.slug());
        var hiddenAuthor = writing.create("bobby", new PostWriteCommand(null, "停用作者", "", null, List.of(), null, "PUBLIC", "隐藏正文"));
        writing.publish("bobby", hiddenAuthor.slug(), hiddenAuthor.version());
        jdbc.update("UPDATE blog_user SET status = 'DISABLED' WHERE username = 'bobby'");
        mvc.perform(get("/api/v1/public/community/posts")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.total").value(21)).andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[*].author.username", everyItem(is("alice"))));
        mvc.perform(get("/api/v1/public/community/posts?page=2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/v1/public/community/posts?page=0")).andExpect(status().isBadRequest());
        assertThat(writing.listOwn("alice", 1, "PUBLIC").total()).isEqualTo(21);
        assertThat(writing.listOwn("alice", 1, "PUBLIC").items()).hasSize(20);
        var first = writing.community(1).items().getFirst(); writing.archive("alice", first.slug(), first.version());
        assertThat(writing.community(1).total()).isEqualTo(20);
    }

    @Test
    void concurrentRecoveryAndHistoryFailureNeverDuplicateOrPartiallyRestore() throws Exception {
        Client admin = loginAdmin(); register(admin, "alice"); grant(admin, "alice", true);
        var p = writing.create("alice", command("归档正文")); writing.archive("alice", p.slug(), p.version());
        String token = writing.listOwn("alice", 1, "ARCHIVED").items().getFirst().version();
        // 用事务内临时约束制造历史写入失败，验证活动行插入也回滚。
        jdbc.execute("ALTER TABLE blog_member_post_revision ADD CONSTRAINT test_recovery_failure CHECK (event_type <> 'RESTORE')");
        try {
            assertThatThrownBy(() -> writing.recoverArchive("alice", p.slug(), token)).isInstanceOf(RuntimeException.class);
            assertThat(writing.listOwn("alice", 1, "ALL").total()).isZero();
            assertThat(writing.listOwn("alice", 1, "ARCHIVED").total()).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE blog_member_post_revision DROP CONSTRAINT test_recovery_failure"); }
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Boolean> recover = () -> { start.await(); try { writing.recoverArchive("alice", p.slug(), token); return true; }
                catch (com.speaive.blog.application.error.BlogException error) { return false; } };
            var left = executor.submit(recover); var right = executor.submit(recover); start.countDown();
            assertThat(List.of(left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(writing.history("alice", p.slug()).items()).hasSize(3);
    }

    @Test
    void editingContextRejectsAccountSwitchAtTheMutationEvenAfterSuccessfulPreflight() throws Exception {
        Client admin = loginAdmin(); Client alice = register(admin, "alice"); Client bob = register(admin, "bobby");
        grant(admin, "alice", true); grant(admin, "bobby", true);
        send(bob, post("/api/v1/account/writing/posts").header("X-Writing-Username", "alice"), content(null))
                .andExpect(status().isForbidden());
        send(bob, put("/api/v1/account/writing/settings/encryption").header("X-Writing-Username", "alice"),
                "{\"encrypted\":true,\"version\":2}").andExpect(status().isForbidden());
        assertThat(writing.listOwn("bobby", 1).total()).isZero();
        assertThat(settings.get("bobby").contentEncrypted()).isFalse();
        send(alice, post("/api/v1/account/writing/posts").header("X-Writing-Username", "alice"), content(null))
                .andExpect(status().isCreated());
    }

    private void assertEncrypted(String username) {
        String id = accounts.findByUsername(username).orElseThrow().id();
        for (String table : List.of("blog_member_post", "blog_member_post_revision")) {
            assertThat(jdbc.queryForList("SELECT payload FROM " + table + " WHERE owner_id = ?", String.class, id))
                    .allSatisfy(value -> assertThat(value).startsWith("v1.test.").doesNotContain("秘密", "私密", "title", "body"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE owner_id = ? AND NOT payload_encrypted", Integer.class, id)).isZero();
        }
    }
    private void grant(Client admin, String username, boolean publish) throws Exception {
        send(admin, put("/api/v1/studio/members/" + username + "/permissions"), permissions(publish, true, 1)).andExpect(status().isOk());
    }
    private String create(Client client) throws Exception {
        return send(client, post("/api/v1/account/writing/posts"), content(null)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }
    private Client register(Client admin, String username) throws Exception {
        String invitation = send(admin, post("/api/v1/studio/invitations"), "{\"maxUses\":1,\"validDays\":7}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Client guest = csrf(null, null);
        String body = "{\"username\":\"" + username + "\",\"displayName\":\"朋友\",\"password\":\"" + PASSWORD + "\",\"invitationCode\":\"" + read(invitation,"$.code") + "\"}";
        MvcResult result = send(guest, post("/api/v1/account/register"), body).andExpect(status().isCreated()).andReturn();
        return csrf((MockHttpSession) result.getRequest().getSession(false), result.getResponse().getCookie("XSRF-TOKEN"));
    }
    private Client loginAdmin() throws Exception {
        Client guest = csrf(null, null);
        MvcResult result = send(guest, post("/api/v1/studio/login"), "{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk()).andReturn();
        return csrf((MockHttpSession) result.getRequest().getSession(false), result.getResponse().getCookie("XSRF-TOKEN"));
    }
    private Client csrf(MockHttpSession session, Cookie cookie) throws Exception {
        var request = get("/api/v1/account/csrf"); if (session != null) request.session(session); if (cookie != null) request.cookie(cookie);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Client(session, cookie == null ? result.getResponse().getCookie("XSRF-TOKEN") : cookie, read(result.getResponse().getContentAsString(), "$.token"));
    }
    private ResultActions send(Client client, MockHttpServletRequestBuilder request, String body) throws Exception {
        if (client.session != null) request.session(client.session);
        return mvc.perform(request.cookie(client.cookie).header("X-XSRF-TOKEN", client.token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private static String read(String json, String path) { return JsonPath.read(json, path); }
    private static String version(String json) { return "{\"version\":\"" + read(json,"$.version") + "\"}"; }
    private static String permissions(boolean publish, boolean encrypt, long version) {
        return "{\"role\":\"WRITER\",\"canPublish\":" + publish + ",\"encryptionAllowed\":" + encrypt + ",\"version\":" + version + "}";
    }
    private static String content(String version) {
        return "{\"title\":\"私密标题\",\"description\":\"秘密摘要\",\"tags\":[\"秘密标签\"],\"body\":\"这是一段秘密正文\",\"visibility\":\"PUBLIC\"" + (version == null ? "" : ",\"version\":\"" + version + "\"") + "}";
    }
    private static PostWriteCommand command(String body) { return new PostWriteCommand(null, "私密标题", "秘密摘要", null, List.of("秘密标签"), null, "ADMIN_ONLY", body); }
    private static Path directory() { try { return Files.createTempDirectory("member-writing-"); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static Path keyFile() {
        try { Path file = DIRECTORY.resolve("keys.properties"); Files.writeString(file,"active=test\nkeys.test=" + Base64.getEncoder().encodeToString(new byte[32])); return file; }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private record Client(MockHttpSession session, Cookie cookie, String token) { }
}
