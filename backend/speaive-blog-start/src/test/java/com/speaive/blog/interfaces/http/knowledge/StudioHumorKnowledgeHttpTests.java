package com.speaive.blog.interfaces.http.knowledge;

import com.speaive.blog.application.command.knowledge.SaveHumorMaterialCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.account.MemberAccountUseCase;
import com.speaive.blog.application.port.in.account.PasswordManagementUseCase;
import com.speaive.blog.application.port.in.knowledge.HumorKnowledgeUseCase;
import com.speaive.blog.application.result.account.AccountCredentialsResult;
import com.speaive.blog.application.result.knowledge.HumorMaterialResult;
import com.speaive.blog.interfaces.security.MemberUserDetails;
import com.speaive.blog.interfaces.security.WebSecurityConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实安全过滤器、请求校验和 MapStruct 映射；用例端口隔离数据库和搜索索引。 */
@WebMvcTest(controllers = StudioHumorKnowledgeController.class)
@Import({WebSecurityConfiguration.class, HumorKnowledgeHttpMapperImpl.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class StudioHumorKnowledgeHttpTests {
    private static final String PATH = "/api/v1/studio/humor-materials";
    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");
    private static final String HASH = new BCryptPasswordEncoder().encode("test-admin-password");
    private static final String BODY = """
            {"excerpt":"原句","sourceUrl":"https://example.com/source","sourceTitle":"来源标题",
             "sourceKind":"PRIMARY_POST","topics":["时间","时钟"],"mechanism":"LITERAL",
             "analysis":"笑点分析","adaptation":"原创示例","quality":4,"enabled":true,"version":1}
            """;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("speaive.security.admin-password-hash", () -> HASH);
    }

    @Autowired MockMvc mvc;
    @MockitoBean HumorKnowledgeUseCase knowledge;
    @MockitoBean MemberAccountUseCase accounts;
    @MockitoBean PasswordManagementUseCase passwords;

    @BeforeEach
    void prepareMemberCredentials() {
        when(passwords.credentialsCurrent("alice", 1)).thenReturn(true);
    }

    @Test
    void anonymousReadsAndAuthenticatedMemberAccessAreRejectedBeforeUseCase() throws Exception {
        for (String path : List.of(PATH, PATH + "/search?q=时间")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            mvc.perform(get(path).with(member())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY).with(cookieCsrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(member()).with(cookieCsrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(knowledge);
    }

    @Test
    void adminCanListMaterialsAndPreviewSearchWithProvenanceAndScores() throws Exception {
        when(knowledge.listMaterials()).thenReturn(List.of(result(1, 0)));
        when(knowledge.searchMaterials("时间", "LITERAL")).thenReturn(List.of(result(1, 2.5)));

        mvc.perform(get(PATH).with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("sample"))
                .andExpect(jsonPath("$.items[0].sourceUrl").value("https://example.com/source"))
                .andExpect(jsonPath("$.items[0].sourceKind").value("PRIMARY_POST"))
                .andExpect(jsonPath("$.items[0].topics[1]").value("时钟"))
                .andExpect(jsonPath("$.items[0].mechanism").value("LITERAL"))
                .andExpect(jsonPath("$.items[0].version").value(1))
                .andExpect(jsonPath("$.items[0].enabled").value(true));
        mvc.perform(get(PATH + "/search").param("q", "时间").param("mechanism", "LITERAL")
                        .with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].score").value(2.5));
        verify(knowledge).listMaterials();
        verify(knowledge).searchMaterials("时间", "LITERAL");
    }

    @Test
    void adminWritesRequireMatchingCookieCsrfTokenBeforeUseCase() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(put(PATH + "/sample").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(user("admin").roles("ADMIN"))
                        .cookie(new Cookie("XSRF-TOKEN", "cookie-token")).header("X-XSRF-TOKEN", "wrong-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(knowledge);
    }

    @Test
    void validAdminCreateMapsAllFieldsAndReturnsCreatedMaterial() throws Exception {
        when(knowledge.createMaterial(any())).thenReturn(result(1, 0));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(user("admin").roles("ADMIN")).with(cookieCsrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("sample"))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.quality").value(4));

        ArgumentCaptor<SaveHumorMaterialCommand> command = ArgumentCaptor.forClass(SaveHumorMaterialCommand.class);
        verify(knowledge).createMaterial(command.capture());
        assertEquals(new SaveHumorMaterialCommand("原句", "https://example.com/source", "来源标题",
                "PRIMARY_POST", List.of("时间", "时钟"), "LITERAL", "笑点分析", "原创示例", 4, true, 1),
                command.getValue());
    }

    @Test
    void validAdminUpdatePassesExpectedVersionAndReturnsIncrementedVersion() throws Exception {
        when(knowledge.updateMaterial(eq("sample"), any())).thenReturn(result(2, 0));

        mvc.perform(put(PATH + "/sample").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(user("admin").roles("ADMIN")).with(cookieCsrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));

        ArgumentCaptor<SaveHumorMaterialCommand> command = ArgumentCaptor.forClass(SaveHumorMaterialCommand.class);
        verify(knowledge).updateMaterial(eq("sample"), command.capture());
        assertEquals(1, command.getValue().version());
    }

    @Test
    void staleUpdateKeeps409CodeAndMessageJsonContract() throws Exception {
        when(knowledge.updateMaterial(eq("sample"), any())).thenThrow(
                new BlogException(BlogErrorCode.VERSION_CONFLICT, "素材已更新，请刷新后重试"));

        mvc.perform(put(PATH + "/sample").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(user("admin").roles("ADMIN")).with(cookieCsrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.message").value("素材已更新，请刷新后重试"));
    }

    @Test
    void invalidRequestFieldsMalformedJsonAndMissingQueryAreRejectedBeforeUseCase() throws Exception {
        for (String invalid : List.of(BODY.replace("[\"时间\",\"时钟\"]", "[]"),
                BODY.replace("\"quality\":4", "\"quality\":0"),
                BODY.replace("\"version\":1", "\"version\":0"),
                BODY.replace("\"excerpt\":\"原句\"", "\"excerpt\":\" \""), "{broken")) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(invalid)
                            .with(user("admin").roles("ADMIN")).with(cookieCsrf()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get(PATH + "/search").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(knowledge);
    }

    private static HumorMaterialResult result(long version, double score) {
        return new HumorMaterialResult("sample", "原句", "https://example.com/source", "来源标题", "PRIMARY_POST",
                List.of("时间", "时钟"), "LITERAL", "笑点分析", "原创示例", 4, true, version, NOW, NOW, score);
    }

    private static RequestPostProcessor member() {
        return user(new MemberUserDetails(new AccountCredentialsResult("alice", HASH, true, 1)));
    }

    /** 使用实际 CookieCsrfTokenRepository，避免测试库替换全局仓库掩盖 Cookie 校验。 */
    private static RequestPostProcessor cookieCsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", "csrf-test-token"));
            request.addHeader("X-XSRF-TOKEN", "csrf-test-token");
            return request;
        };
    }
}
