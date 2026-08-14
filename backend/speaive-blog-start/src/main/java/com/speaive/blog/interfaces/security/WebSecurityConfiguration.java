package com.speaive.blog.interfaces.security;

import com.speaive.blog.interfaces.http.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@Configuration(proxyBeanMethods = false)
public class WebSecurityConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSecurityConfiguration.class);
    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${speaive.security.secure-cookies:false}") boolean secureCookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secureCookies));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${speaive.security.admin-username:admin}") String username,
            @Value("${speaive.security.admin-password-hash:}") String configuredHash,
            PasswordEncoder passwordEncoder) {
        String normalizedUsername = username.trim();
        if (normalizedUsername.isEmpty() || normalizedUsername.length() > 100) {
            throw new IllegalStateException("SPEAIVE_ADMIN_USERNAME 必须为 1 到 100 个字符");
        }

        String passwordHash = configuredHash.trim();
        if (passwordHash.isEmpty()) {
            passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());
            LOGGER.warn("未配置 SPEAIVE_ADMIN_PASSWORD_HASH，写作台登录已禁用");
        } else if (!BCRYPT_HASH.matcher(passwordHash).matches()) {
            throw new IllegalStateException("SPEAIVE_ADMIN_PASSWORD_HASH 必须是 BCrypt 哈希");
        }

        return new InMemoryUserDetailsManager(User.withUsername(normalizedUsername)
                .password(passwordHash)
                .roles("ADMIN")
                .build());
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    LoginAttemptLimiter loginAttemptLimiter(
            @Value("${speaive.security.login-max-attempts:5}") int maxAttempts,
            @Value("${speaive.security.login-window-seconds:900}") long windowSeconds,
            @Value("${speaive.security.login-max-tracked-addresses:10000}") int maxTrackedAddresses) {
        return new LoginAttemptLimiter(maxAttempts, Duration.ofSeconds(windowSeconds), maxTrackedAddresses);
    }

    @Bean
    SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**", "/media/**",
                                "/api/v1/studio/csrf", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/studio/login").permitAll()
                        .requestMatchers("/api/v1/studio/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        ApiErrorCode.UNAUTHORIZED, "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, HttpServletResponse.SC_FORBIDDEN,
                                        ApiErrorCode.FORBIDDEN, "请求未通过安全校验")));
        return http.build();
    }

    private static void writeSecurityError(
            HttpServletResponse response, int status, ApiErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code.name() + "\",\"message\":\"" + message + "\"}");
    }
}
