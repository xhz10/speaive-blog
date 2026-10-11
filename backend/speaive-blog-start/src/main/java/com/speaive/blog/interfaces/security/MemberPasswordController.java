package com.speaive.blog.interfaces.security;

import com.speaive.blog.application.command.account.*;
import com.speaive.blog.application.port.in.account.MemberAccountUseCase;
import com.speaive.blog.application.port.in.account.PasswordManagementUseCase;
import com.speaive.blog.interfaces.http.error.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

/** 密码原文仅在安全入站层校验与编码，用例收到新哈希与已验证的旧凭证版本。 */
@RestController
@RequestMapping("/api/v1/account/password")
public class MemberPasswordController {
    private final MemberAccountUseCase accounts;
    private final PasswordManagementUseCase management;
    private final PasswordEncoder passwords;
    private final CookieCsrfTokenRepository csrf;
    private final LoginAttemptLimiter changes;
    private final LoginAttemptLimiter resets;
    public MemberPasswordController(MemberAccountUseCase accounts, PasswordManagementUseCase management,
            PasswordEncoder passwords, CookieCsrfTokenRepository csrf,
            @Qualifier("passwordChangeAttemptLimiter") LoginAttemptLimiter changes,
            @Qualifier("passwordResetAttemptLimiter") LoginAttemptLimiter resets) {
        this.accounts = accounts; this.management = management; this.passwords = passwords;
        this.csrf = csrf; this.changes = changes; this.resets = resets;
    }

    @PostMapping("/change")
    ResponseEntity<Void> change(Authentication auth, @RequestHeader("X-Writing-Username") String expectedUsername,
            @Valid @RequestBody ChangeRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        if (!auth.getName().equals(expectedUsername)) throw new ApiHttpException(ApiErrorCode.FORBIDDEN,
                "登录账号已改变，请重新登录原账号后再修改密码", HttpStatus.FORBIDDEN);
        changes.consume(servletRequest.getRemoteAddr() + ":" + auth.getName());
        var credentials = accounts.findCredentials(auth.getName()).orElseThrow(MemberPasswordController::invalidCredentials);
        if (!(auth.getPrincipal() instanceof MemberUserDetails principal)
                || principal.credentialVersion() != credentials.credentialVersion() || !credentials.enabled()) {
            throw new ApiHttpException(ApiErrorCode.UNAUTHORIZED, "密码已更新，请重新登录", HttpStatus.UNAUTHORIZED);
        }
        if (!MemberPasswordPolicy.matches(request.currentPassword(), credentials.passwordHash(), passwords)) throw invalidCredentials();
        String nextHash = MemberPasswordPolicy.encode(request.newPassword(), passwords);
        management.changePassword(auth.getName(), new ChangePasswordCommand(credentials.credentialVersion(), nextHash));
        new SecurityContextLogoutHandler().logout(servletRequest, response, auth);
        csrf.saveToken(null, servletRequest, response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest request, HttpServletRequest servletRequest) {
        resets.consume(servletRequest.getRemoteAddr());
        String nextHash = MemberPasswordPolicy.encode(request.newPassword(), passwords);
        management.resetPassword(new ResetPasswordCommand(request.token(), nextHash));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static ApiHttpException invalidCredentials() {
        return new ApiHttpException(ApiErrorCode.INVALID_CREDENTIALS, "当前密码不正确", HttpStatus.UNAUTHORIZED);
    }
    record ChangeRequest(@NotBlank(message = "当前密码不能为空") String currentPassword,
            @NotBlank(message = "新密码不能为空") String newPassword) {
        @Override public String toString() { return "ChangeRequest[密码已隐藏]"; }
    }
    record ResetRequest(@NotBlank(message = "找回令牌不能为空") @Size(max = 100, message = "找回链接无效") String token,
            @NotBlank(message = "新密码不能为空") String newPassword) {
        @Override public String toString() { return "ResetRequest[凭证已隐藏]"; }
    }
}
