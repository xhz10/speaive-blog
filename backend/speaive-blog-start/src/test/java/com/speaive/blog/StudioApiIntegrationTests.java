package com.speaive.blog;

import com.jayway.jsonpath.JsonPath;
import com.speaive.blog.application.BlogErrorCode;
import com.speaive.blog.application.BlogException;
import com.speaive.blog.application.ContentStorePort;
import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.domain.PostStatus;
import com.speaive.blog.domain.PostVisibility;
import com.speaive.blog.infrastructure.content.MarkdownInboxImporter;
import com.speaive.blog.infrastructure.content.BlogPersistenceMapper;
import db.migration.V1__Create_blog_schema;
import db.migration.V2__Add_post_visibility_and_media_access;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private static final String PASSWORD = "speaive-test-password";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final Path DATA_DIRECTORY = createTempDirectory();

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
    private final ContentStorePort contentStore;
    private final BlogPersistenceMapper mapper;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    StudioApiIntegrationTests(
            MockMvc mockMvc,
            JdbcTemplate jdbc,
            MarkdownInboxImporter inboxImporter,
            ContentStorePort contentStore,
            BlogPersistenceMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.inboxImporter = inboxImporter;
        this.contentStore = contentStore;
        this.mapper = mapper;
        this.transactionManager = transactionManager;
    }

    @BeforeEach
    void clearContent() throws Exception {
        jdbc.update("DELETE FROM blog_post_media");
        jdbc.update("DELETE FROM blog_post_revision_tag");
        jdbc.update("DELETE FROM blog_post_revision");
        jdbc.update("DELETE FROM blog_markdown_import");
        jdbc.update("DELETE FROM blog_media");
        jdbc.update("DELETE FROM blog_post_tag");
        jdbc.update("DELETE FROM blog_post");
        clearDirectory(DATA_DIRECTORY);
        Files.createDirectories(DATA_DIRECTORY.resolve("inbox"));
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
    void visibilityMigrationBackfillsExistingPostsRevisionsAndMediaReferences() throws Exception {
        String schema = "visibility_migration_test";
        try (Connection connection = jdbc.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET search_path TO " + schema + ", public");

            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V1__Create_blog_schema().migrate(context);

            statement.execute("""
                    INSERT INTO blog_media (
                        relative_path, original_file_name, mime_type, size_bytes, sha256, created_at
                    ) VALUES (
                        '2026/08/existing.png', 'existing.png', 'image/png', 1,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', now()
                    )
                    """);
            statement.execute("""
                    INSERT INTO blog_post (
                        id, slug, title, description, published_at, updated_at, status,
                        body, cover, revision, created_at
                    ) VALUES (
                        'existing-post', 'existing-post', '旧文章', '', now(), now(), 'PUBLISHED',
                        '![旧图片](/media/2026/08/existing.png)',
                        '/media/2026/08/existing.png', 1, now()
                    )
                    """);
            statement.execute("""
                    INSERT INTO blog_post_revision (
                        post_id, revision, slug, title, description, published_at, updated_at,
                        status, body, cover, post_created_at, event_type, recorded_at
                    ) SELECT id, revision, slug, title, description, published_at, updated_at,
                             status, body, cover, created_at, 'CREATE', now()
                      FROM blog_post WHERE id = 'existing-post'
                    """);

            new V2__Add_post_visibility_and_media_access().migrate(context);

            assertThat(singleString(statement,
                    "SELECT visibility FROM blog_post WHERE id = 'existing-post'"))
                    .isEqualTo("PUBLIC");
            assertThat(singleString(statement,
                    "SELECT visibility FROM blog_post_revision WHERE post_id = 'existing-post'"))
                    .isEqualTo("PUBLIC");
            assertThat(singleInt(statement,
                    "SELECT COUNT(*) FROM blog_post_media WHERE post_id = 'existing-post'"))
                    .isEqualTo(1);

            statement.execute("""
                    INSERT INTO blog_post (
                        id, slug, title, description, published_at, updated_at, status,
                        body, cover, revision, created_at
                    ) VALUES (
                        'new-post', 'new-post', '新文章', '', now(), now(), 'DRAFT',
                        '正文', NULL, 1, now()
                    )
                    """);
            assertThat(singleString(statement,
                    "SELECT visibility FROM blog_post WHERE id = 'new-post'"))
                    .isEqualTo("ADMIN_ONLY");
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS visibility_migration_test CASCADE");
        }
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
                .andExpect(jsonPath("$.title").value("第一篇随记（已保存）"));

        mockMvc.perform(get("/api/v1/studio/posts").session(client.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
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
                .andExpect(jsonPath("$.html", containsString("/api/v1/studio/media/2026/08/a.png")));
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
                .andExpect(jsonPath("$.visibility").value("ADMIN_ONLY"))
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
        ContentStorePort failingStore = mock(ContentStorePort.class);
        when(failingStore.importDraft(eq("retry.md"), any(byte[].class)))
                .thenThrow(new IllegalStateException("database temporarily unavailable"));
        MarkdownInboxImporter importer = new MarkdownInboxImporter(
                retryInbox, 1_048_576, failingStore, mapper, new TransactionTemplate(transactionManager));

        importer.scanNow();

        assertThat(retryInbox.resolve("retry.md")).isRegularFile();
        try (var rejected = Files.list(retryInbox.resolve("rejected"))) {
            assertThat(rejected).isEmpty();
        }
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
                .andExpect(jsonPath("$.version", endsWith(":1")));

        byte[] signatureOnly = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile incompleteImage = new MockMultipartFile("image", "incomplete.png", "image/png",
                signatureOnly);
        mockMvc.perform(multipart("/api/v1/studio/media").file(incompleteImage)
                        .session(client.session()).cookie(client.csrfCookie())
                        .header(client.csrfHeader(), client.csrfToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));

        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
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
        String studioUrl = "/api/v1/studio" + url;

        mockMvc.perform(get(url))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(studioUrl))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(studioUrl).session(client.session()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM blog_media", Integer.class)).isEqualTo(1);

        byte[] replaced = png.clone();
        replaced[replaced.length - 1] ^= 1;
        Files.write(DATA_DIRECTORY.resolve(url.substring(1)), replaced);
        mockMvc.perform(get(studioUrl).session(client.session()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"));

        Files.delete(DATA_DIRECTORY.resolve(url.substring(1)));
        mockMvc.perform(get(studioUrl).session(client.session()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"));

        Path unregistered = DATA_DIRECTORY.resolve("media/2026/08/unregistered.png");
        Files.createDirectories(unregistered.getParent());
        Files.write(unregistered, png);
        mockMvc.perform(get("/media/2026/08/unregistered.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminOnlyPostsAndTheirMediaNeverLeakThroughPublicEndpoints() throws Exception {
        Client client = login();
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile image = new MockMultipartFile("image", "secret.png", "image/png", png);
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
                .andExpect(jsonPath("$.html", containsString(studioMediaUrl)))
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
                .andExpect(content().bytes(png));

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
                .andExpect(content().bytes(png));

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
        String version = contentStore.createDraft(command("concurrent-post", "并发文章")).version();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = List.of(0, 1).stream().map(index -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    contentStore.update("concurrent-post", version,
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
        String oldVersion = contentStore.createDraft(command("reused-slug", "旧文章")).version();
        contentStore.archive("reused-slug", oldVersion);
        String newVersion = contentStore.createDraft(command("reused-slug", "新文章")).version();

        assertThat(newVersion).isNotEqualTo(oldVersion).endsWith(":1");
        assertThatThrownBy(() -> contentStore.update(
                "reused-slug", oldVersion, command("reused-slug", "旧页面误保存")))
                .isInstanceOfSatisfying(BlogException.class,
                        exception -> assertThat(exception.code()).isEqualTo(BlogErrorCode.VERSION_CONFLICT));
        assertThat(contentStore.find("reused-slug", true)).hasValueSatisfying(post -> {
            assertThat(post.title()).isEqualTo("新文章");
            assertThat(post.version()).isEqualTo(newVersion);
        });
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
                    contentStore.createDraft(command("same-slug", "并发创建 " + index));
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
        String version = contentStore.createDraft(command("rollback-archive", "归档事务")).version();
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
            assertThatThrownBy(() -> contentStore.archive("rollback-archive", version))
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
                List.of("测试"), null, PostVisibility.ADMIN_ONLY, "正文");
    }

    private static String singleString(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static int singleInt(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
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
