package com.speaive.blog.interfaces.security;

import com.speaive.blog.interfaces.http.error.ApiErrorCode;
import com.speaive.blog.interfaces.http.error.ApiHttpException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录入口的尝试次数限制器，限制短时间内的重复认证失败；不承担会员或作者业务状态管理。
 */
public final class LoginAttemptLimiter {
    private static final Duration MAX_CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final int maxAttempts;
    private final Duration window;
    private final int maxTrackedAddresses;
    private final Clock clock;
    private final Map<String, AttemptWindow> attempts = new HashMap<>();
    private Instant nextCleanupAt = Instant.EPOCH;

    public LoginAttemptLimiter(int maxAttempts, Duration window, int maxTrackedAddresses) {
        this(maxAttempts, window, maxTrackedAddresses, Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxAttempts, Duration window, int maxTrackedAddresses, Clock clock) {
        if (maxAttempts <= 0 || window.isZero() || window.isNegative() || maxTrackedAddresses <= 0) {
            throw new IllegalArgumentException("登录限流配置必须大于 0");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.maxTrackedAddresses = maxTrackedAddresses;
        this.clock = clock;
    }

    public synchronized void consume(String clientAddress) {
        Instant now = clock.instant();
        cleanupExpiredIfDue(now, false);
        String key = normalizeAddress(clientAddress);
        AttemptWindow current = attempts.get(key);
        if (current != null && expired(current, now)) {
            attempts.remove(key);
            current = null;
        }
        if (current != null && current.attempts() >= maxAttempts) {
            throw rateLimited();
        }
        if (current == null) {
            if (attempts.size() >= maxTrackedAddresses) {
                cleanupExpiredIfDue(now, true);
            }
            if (attempts.size() >= maxTrackedAddresses) {
                throw rateLimited();
            }
            attempts.put(key, new AttemptWindow(now, 1));
            return;
        }
        attempts.put(key, new AttemptWindow(current.startedAt(), current.attempts() + 1));
    }

    public synchronized void reset(String clientAddress) {
        attempts.remove(normalizeAddress(clientAddress));
    }

    synchronized int trackedAddressCount() {
        return attempts.size();
    }

    private void cleanupExpiredIfDue(Instant now, boolean force) {
        if (!force && now.isBefore(nextCleanupAt)) {
            return;
        }
        attempts.entrySet().removeIf(entry -> expired(entry.getValue(), now));
        Duration cleanupInterval = window.compareTo(MAX_CLEANUP_INTERVAL) < 0 ? window : MAX_CLEANUP_INTERVAL;
        nextCleanupAt = now.plus(cleanupInterval);
    }

    private boolean expired(AttemptWindow attemptWindow, Instant now) {
        return !now.isBefore(attemptWindow.startedAt().plus(window));
    }

    private static String normalizeAddress(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
    }

    private static ApiHttpException rateLimited() {
        return new ApiHttpException(ApiErrorCode.RATE_LIMITED, "登录尝试过于频繁，请稍后再试",
                HttpStatus.TOO_MANY_REQUESTS);
    }

    private record AttemptWindow(Instant startedAt, int attempts) {
    }
}
