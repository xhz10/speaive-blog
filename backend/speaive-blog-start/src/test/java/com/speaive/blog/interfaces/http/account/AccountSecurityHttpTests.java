package com.speaive.blog.interfaces.http.account;

import com.jayway.jsonpath.JsonPath;
import com.speaive.blog.application.command.account.ChangePasswordCommand;
import com.speaive.blog.application.command.account.CreateInvitationCommand;
import com.speaive.blog.application.command.account.ResetPasswordCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.account.MemberAccountUseCase;
import com.speaive.blog.application.port.in.account.PasswordManagementUseCase;
import com.speaive.blog.application.result.account.AccountCredentialsResult;
import com.speaive.blog.application.result.account.InvitationListResult;
import com.speaive.blog.application.result.account.InvitationResult;
import com.speaive.blog.application.result.account.MemberResult;
import com.speaive.blog.application.result.account.PasswordResetResult;
import com.speaive.blog.interfaces.security.LoginAttemptLimiter;
import com.speaive.blog.interfaces.security.MemberAuthenticationController;
import com.speaive.blog.interfaces.security.MemberPasswordController;
import com.speaive.blog.interfaces.security.MemberUserDetails;
import com.speaive.blog.interfaces.security.StudioAuthenticationController;
import com.speaive.blog.interfaces.security.WebSecurityConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实安全过滤器与 BCrypt 的 HTTP 回归测试，用例端口隔离数据库与令牌生命周期规则。 */
@WebMvcTest(controllers = {MemberAuthenticationController.class, StudioAuthenticationController.class,
        MemberPasswordController.class, StudioPasswordRecoveryController.class, StudioInvitationController.class})
@Import({WebSecurityConfiguration.class, InvitationHttpMapperImpl.class, PasswordRecoveryHttpMapperImpl.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
class AccountSecurityHttpTests {
    private static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder();
    private static final String PASSWORD = "member-test-password";
    private static final String HASH = PASSWORDS.encode(PASSWORD);
    private static final String ADMIN_PASSWORD = "a".repeat(72);
    private static final String ADMIN_HASH = PASSWORDS.encode(ADMIN_PASSWORD);
    private static final Instant NOW = Instant.parse("2026-10-11T01:00:00Z");
    private static final AccountCredentialsResult CREDENTIALS = new AccountCredentialsResult("alice", HASH, true, 1);
    private static final String CHANGE = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"next-member-password\"}";
    private static final String RESET = "{\"token\":\"one-time-token\",\"newPassword\":\"next-member-password\"}";

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("speaive.security.admin-username", () -> "admin");
        registry.add("speaive.security.admin-password-hash", () -> ADMIN_HASH);
    }

    @Autowired MockMvc mvc;
    @MockitoBean MemberAccountUseCase accounts;
    @MockitoBean PasswordManagementUseCase management;
    @Autowired @Qualifier("passwordChangeAttemptLimiter") LoginAttemptLimiter changes;
    @Autowired @Qualifier("passwordResetAttemptLimiter") LoginAttemptLimiter resets;
    @Autowired @Qualifier("memberLoginAttemptLimiter") LoginAttemptLimiter memberLogins;
    @Autowired @Qualifier("loginAttemptLimiter") LoginAttemptLimiter adminLogins;
    @Autowired @Qualifier("registrationAttemptLimiter") LoginAttemptLimiter registrations;

    @BeforeEach
    void prepare() {
        when(accounts.findCredentials("alice")).thenReturn(Optional.of(CREDENTIALS));
        when(accounts.getMember("alice")).thenReturn(new MemberResult("member-id", "alice", "Alice", true, NOW));
        when(management.credentialsCurrent("alice", 1)).thenReturn(true);
        changes.reset("127.0.0.1:alice");
        resets.reset("127.0.0.1");
        memberLogins.reset("127.0.0.1");
        adminLogins.reset("127.0.0.1");
        registrations.reset("127.0.0.1");
    }

    @Test
    void passwordChangeRequiresMemberAuthenticationAndCsrf() throws Exception {
        mvc.perform(jsonPost("/api/v1/account/password/change", CHANGE).with(cookieCsrf()).header("X-Writing-Username", "alice"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(jsonPost("/api/v1/account/password/change", CHANGE).with(user("admin").roles("ADMIN"))
                        .with(cookieCsrf()).header("X-Writing-Username", "alice"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(jsonPost("/api/v1/account/password/change", CHANGE).session(memberSession())
                        .header("X-Writing-Username", "alice"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(management, never()).changePassword(any(), any());
    }

    @Test
    void passwordChangeRejectsMissingOrChangedAccountHeaderAndWrongCurrentPassword() throws Exception {
        mvc.perform(jsonPost("/api/v1/account/password/change", CHANGE).session(memberSession()).with(cookieCsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(jsonPost("/api/v1/account/password/change", CHANGE).session(memberSession()).with(cookieCsrf())
                        .header("X-Writing-Username", "bobby"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(changeRequest(CHANGE.replace(PASSWORD, "wrong-current-password")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(changeRequest(CHANGE.replace(PASSWORD, "密".repeat(25))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        verify(management, never()).changePassword(any(), any());
    }

    @Test
    void passwordChangeHashesTheNewPasswordAndInvalidatesSessionAndCsrfCookie() throws Exception {
        MockHttpSession session = memberSession();
        var csrfResponse = mvc.perform(get("/api/v1/account/csrf").session(session)).andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        String token = JsonPath.read(csrfResponse.getContentAsString(), "$.token");
        assertThat(csrfCookie).isNotNull();
        mvc.perform(jsonPost("/api/v1/account/password/change", CHANGE).session(session)
                        .cookie(csrfCookie).header("X-XSRF-TOKEN", token).header("X-Writing-Username", "alice"))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
        ArgumentCaptor<ChangePasswordCommand> command = ArgumentCaptor.forClass(ChangePasswordCommand.class);
        verify(management).changePassword(eq("alice"), command.capture());
        assertThat(command.getValue().verifiedCredentialVersion()).isEqualTo(1);
        assertThat(PASSWORDS.matches("next-member-password", command.getValue().newPasswordHash())).isTrue();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void credentialChangeBetweenFilterAndControllerCannotAuthorizeAnotherChange() throws Exception {
        when(accounts.findCredentials("alice")).thenReturn(Optional.of(new AccountCredentialsResult("alice", HASH, true, 2)));
        mvc.perform(changeRequest(CHANGE)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(management, never()).changePassword(any(), any());
    }

    @Test
    void registrationAndPasswordEndpointsRejectOver72Utf8BytesBeforeMutating() throws Exception {
        String oversized = "密".repeat(25);
        mvc.perform(jsonPost("/api/v1/account/register", "{\"username\":\"bobby\",\"displayName\":\"Bob\",\"password\":\""
                        + oversized + "\",\"invitationCode\":\"valid-invitation-code\"}").with(cookieCsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(changeRequest(CHANGE.replace("next-member-password", oversized)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(jsonPost("/api/v1/account/password/reset", RESET.replace("next-member-password", oversized)).with(cookieCsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(accounts, never()).register(any());
        verify(management, never()).changePassword(any(), any());
        verify(management, never()).resetPassword(any());
    }

    @Test
    void anonymousResetRequiresCsrfAndAcceptsExactly72Utf8Bytes() throws Exception {
        mvc.perform(jsonPost("/api/v1/account/password/reset", RESET))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(management, never()).resetPassword(any());
        String boundary = "密".repeat(24);
        mvc.perform(jsonPost("/api/v1/account/password/reset", RESET.replace("next-member-password", boundary)).with(cookieCsrf()))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", containsString("no-store")));
        ArgumentCaptor<ResetPasswordCommand> command = ArgumentCaptor.forClass(ResetPasswordCommand.class);
        verify(management).resetPassword(command.capture());
        assertThat(command.getValue().token()).isEqualTo("one-time-token");
        assertThat(PASSWORDS.matches(boundary, command.getValue().newPasswordHash())).isTrue();
    }

    @Test
    void invalidResetInputAndExpiredTokenKeepApiErrorStructure() throws Exception {
        mvc.perform(jsonPost("/api/v1/account/password/reset", "{\"token\":\"\",\"newPassword\":\"next-member-password\"}").with(cookieCsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(jsonPost("/api/v1/account/password/reset", "{broken").with(cookieCsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        doThrow(new BlogException(BlogErrorCode.INVALID_REQUEST, "找回链接已过期或已使用"))
                .when(management).resetPassword(any());
        mvc.perform(jsonPost("/api/v1/account/password/reset", RESET).with(cookieCsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("找回链接已过期或已使用"));
    }

    @Test
    void resetAttemptsAreLimitedIndependentlyFromMemberLogin() throws Exception {
        doThrow(new BlogException(BlogErrorCode.INVALID_REQUEST, "找回链接无效"))
                .when(management).resetPassword(any());
        for (int index = 0; index < 10; index++) {
            mvc.perform(jsonPost("/api/v1/account/password/reset", RESET).with(cookieCsrf())).andExpect(status().isBadRequest());
        }
        mvc.perform(jsonPost("/api/v1/account/password/reset", RESET).with(cookieCsrf()))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        mvc.perform(jsonPost("/api/v1/account/login", login("alice", PASSWORD)).with(cookieCsrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void loginStoresCredentialVersionAndRejectsTruncatedBcryptSuffixes() throws Exception {
        String boundary = "密".repeat(24);
        when(accounts.findCredentials("alice")).thenReturn(Optional.of(
                new AccountCredentialsResult("alice", PASSWORDS.encode(boundary), true, 7)));
        var result = mvc.perform(jsonPost("/api/v1/account/login", login("alice", boundary)).with(cookieCsrf()))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        var context = (org.springframework.security.core.context.SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        MemberUserDetails principal = (MemberUserDetails) context.getAuthentication().getPrincipal();
        assertThat(principal.credentialVersion()).isEqualTo(7);
        assertThat(principal.getPassword()).isNull();
        mvc.perform(jsonPost("/api/v1/account/login", login("alice", boundary + "a")).with(cookieCsrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(jsonPost("/api/v1/studio/login", login("admin", ADMIN_PASSWORD)).with(cookieCsrf()))
                .andExpect(status().isOk());
        mvc.perform(jsonPost("/api/v1/studio/login", login("admin", ADMIN_PASSWORD + "a")).with(cookieCsrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void staleMemberSessionIsInvalidatedBeforeAccountAccessAndOnPublicRequests() throws Exception {
        when(management.credentialsCurrent("alice", 1)).thenReturn(false);
        MockHttpSession first = memberSession();
        mvc.perform(get("/api/v1/account/session").session(first))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(first.isInvalid()).isTrue();
        verify(accounts, never()).getMember("alice");
        MockHttpSession second = memberSession();
        mvc.perform(get("/api/v1/account/csrf").session(second)).andExpect(status().isOk());
        assertThat(second.isInvalid()).isTrue();
    }

    @Test
    void legacyMemberPrincipalWithoutCredentialVersionCannotKeepUsingSession() throws Exception {
        mvc.perform(get("/api/v1/account/session").with(user("alice").roles("MEMBER")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(accounts, never()).getMember("alice");
    }

    @Test
    void onlyAdminWithCsrfCanIssueRecoveryAndRevokeInvitations() throws Exception {
        for (String path : List.of("/api/v1/studio/members/alice/password-reset", "/api/v1/studio/invitations/invitation-id/revoke")) {
            mvc.perform(jsonPost(path, "{}").with(cookieCsrf())).andExpect(status().isUnauthorized());
            mvc.perform(jsonPost(path, "{}").session(memberSession()).with(cookieCsrf())).andExpect(status().isForbidden());
            mvc.perform(jsonPost(path, "{}").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        }
        verify(management, never()).issueReset(any());
        verify(accounts, never()).revokeInvitation(any());
    }

    @Test
    void recoveryAndInvitationResponsesAreUncachedAndIncludeNewManagementFields() throws Exception {
        when(management.issueReset("alice")).thenReturn(new PasswordResetResult("alice", "fresh-token", NOW.plusSeconds(1800)));
        mvc.perform(jsonPost("/api/v1/studio/members/alice/password-reset", "{}").with(user("admin").roles("ADMIN")).with(cookieCsrf()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.username").value("alice")).andExpect(jsonPath("$.token").value("fresh-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-11T01:30:00Z"));
        InvitationResult invitation = new InvitationResult("invitation-id", "invitation-code", 3, 1, NOW.plusSeconds(3600), NOW,
                "送给朋友", NOW.plusSeconds(1));
        when(accounts.revokeInvitation("invitation-id")).thenReturn(invitation);
        mvc.perform(jsonPost("/api/v1/studio/invitations/invitation-id/revoke", "{}").with(user("admin").roles("ADMIN")).with(cookieCsrf()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.note").value("送给朋友")).andExpect(jsonPath("$.revokedAt").value("2026-10-11T01:00:01Z"));
        when(accounts.listInvitations()).thenReturn(new InvitationListResult(List.of(invitation)));
        mvc.perform(get("/api/v1/studio/invitations").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.items[0].revokedAt").value("2026-10-11T01:00:01Z"));
    }

    @Test
    void invitationCreationMapsNotesAndRejectsOversizedNotes() throws Exception {
        when(accounts.createInvitation(any())).thenReturn(new InvitationResult("invitation-id", "invitation-code", 3, 0,
                NOW.plusSeconds(3600), NOW, "送给朋友", null));
        mvc.perform(jsonPost("/api/v1/studio/invitations", "{\"validDays\":7,\"maxUses\":3,\"note\":\"送给朋友\"}")
                        .with(user("admin").roles("ADMIN")).with(cookieCsrf()))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.note").value("送给朋友"));
        ArgumentCaptor<CreateInvitationCommand> command = ArgumentCaptor.forClass(CreateInvitationCommand.class);
        verify(accounts).createInvitation(command.capture());
        assertThat(command.getValue()).isEqualTo(new CreateInvitationCommand(7, 3, "送给朋友"));
        mvc.perform(jsonPost("/api/v1/studio/invitations", "{\"validDays\":7,\"maxUses\":3,\"note\":\"" + "长".repeat(201) + "\"}")
                        .with(user("admin").roles("ADMIN")).with(cookieCsrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** 使用实际 CookieCsrfTokenRepository，避免安全测试库替换全局仓库后掩盖 Cookie 回归。 */
    private static RequestPostProcessor cookieCsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", "csrf-test-token"));
            request.addHeader("X-XSRF-TOKEN", "csrf-test-token");
            return request;
        };
    }

    private static MockHttpServletRequestBuilder jsonPost(String path, String content) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private static MockHttpServletRequestBuilder changeRequest(String content) {
        return jsonPost("/api/v1/account/password/change", content).session(memberSession()).with(cookieCsrf())
                .header("X-Writing-Username", "alice");
    }

    private static MockHttpSession memberSession() {
        MemberUserDetails principal = new MemberUserDetails(CREDENTIALS);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private static String login(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
