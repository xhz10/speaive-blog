package com.speaive.blog.interfaces.security;

import com.speaive.blog.application.port.in.account.PasswordManagementUseCase;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** 每次请求核验会员凭证版本，包括写作区域外的会员功能；旧 Session 在下次访问时统一失效。 */
public final class MemberCredentialVersionFilter extends OncePerRequestFilter {
    private final PasswordManagementUseCase passwords;
    public MemberCredentialVersionFilter(PasswordManagementUseCase passwords) { this.passwords = passwords; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getAuthorities().stream()
                .anyMatch(value -> "ROLE_MEMBER".equals(value.getAuthority()))) {
            boolean current = authentication.getPrincipal() instanceof MemberUserDetails member
                    && passwords.credentialsCurrent(member.getUsername(), member.credentialVersion());
            if (!current) new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        chain.doFilter(request, response);
    }
}
