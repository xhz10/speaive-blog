package com.speaive.blog.interfaces.security;

import com.speaive.blog.application.command.account.RegisterMemberCommand;
import com.speaive.blog.application.port.in.account.MemberAccountUseCase;
import com.speaive.blog.application.result.account.MemberResult;
import com.speaive.blog.interfaces.http.error.ApiErrorCode;
import com.speaive.blog.interfaces.http.error.ApiHttpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
public class MemberAuthenticationController {
    private final MemberAccountUseCase accounts;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public MemberAuthenticationController(
            MemberAccountUseCase accounts,
            PasswordEncoder passwords,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            LoginAttemptLimiter loginAttemptLimiter) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @GetMapping("/session")
    MemberResult session(Authentication authentication) {
        return accounts.getMember(authentication.getName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    MemberResult register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        String clientAddress = servletRequest.getRemoteAddr();
        loginAttemptLimiter.consume(clientAddress);
        MemberResult member = accounts.register(new RegisterMemberCommand(
                request.username(), request.displayName(), passwords.encode(request.password()),
                request.invitationCode()));
        authenticate(request.username(), request.password(), servletRequest, response, clientAddress);
        return member;
    }

    @PostMapping("/login")
    MemberResult login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        String clientAddress = servletRequest.getRemoteAddr();
        loginAttemptLimiter.consume(clientAddress);
        Authentication authentication = authenticate(
                request.username(), request.password(), servletRequest, response, clientAddress);
        return accounts.getMember(authentication.getName());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
    }

    private Authentication authenticate(
            String username,
            String password,
            HttpServletRequest request,
            HttpServletResponse response,
            String clientAddress) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            boolean member = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_MEMBER"));
            if (!member) {
                throw new org.springframework.security.authentication.BadCredentialsException("not member");
            }
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) existingSession.invalidate();
            request.getSession(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            CsrfToken rotated = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(rotated, request, response);
            loginAttemptLimiter.reset(clientAddress);
            return authentication;
        } catch (AuthenticationException exception) {
            throw new ApiHttpException(
                    ApiErrorCode.INVALID_CREDENTIALS, "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
    }

    record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{2,49}$",
                    message = "用户名必须为 3 到 50 位小写字母、数字、下划线或连字符")
            String username,
            @NotBlank(message = "显示名称不能为空")
            @Size(max = 100, message = "显示名称不能超过 100 个字符")
            String displayName,
            @NotBlank(message = "密码不能为空")
            @Size(min = 10, max = 100, message = "密码长度必须为 10 到 100 个字符")
            String password,
            @NotBlank(message = "邀请码不能为空")
            @Size(min = 12, max = 100, message = "邀请码格式无效")
            String invitationCode
    ) {
    }

    record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }

    record CsrfResponse(String token, String headerName, String parameterName) {
    }
}
