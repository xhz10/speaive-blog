package com.speaive.blog.interfaces.security;

import com.speaive.blog.interfaces.http.error.ApiErrorCode;
import com.speaive.blog.interfaces.http.error.ApiHttpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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
@RequestMapping("/api/v1/studio")
public class StudioAuthenticationController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public StudioAuthenticationController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            LoginAttemptLimiter loginAttemptLimiter) {
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
    SessionResponse session(Authentication authentication) {
        return new SessionResponse(authentication.getName());
    }

    @PostMapping("/login")
    SessionResponse login(
            @Valid @RequestBody LoginRequest login,
            HttpServletRequest request,
            HttpServletResponse response) {
        String clientAddress = request.getRemoteAddr();
        loginAttemptLimiter.consume(clientAddress);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
            boolean admin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            if (!admin) {
                throw new org.springframework.security.authentication.BadCredentialsException("not admin");
            }

            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
            request.getSession(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            CsrfToken rotatedCsrfToken = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(rotatedCsrfToken, request, response);
            loginAttemptLimiter.reset(clientAddress);
            return new SessionResponse(authentication.getName());
        } catch (AuthenticationException exception) {
            throw new ApiHttpException(ApiErrorCode.INVALID_CREDENTIALS, "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
    }

    record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }

    record SessionResponse(String username) {
    }

    record CsrfResponse(String token, String headerName, String parameterName) {
    }
}
