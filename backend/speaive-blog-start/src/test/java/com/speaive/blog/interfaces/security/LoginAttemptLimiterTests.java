package com.speaive.blog.interfaces.security;

import com.speaive.blog.interfaces.http.ApiHttpException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptLimiterTests {

    @Test
    void concurrentAttemptsAtomicallyConsumeTheSameAddressBudget() throws Exception {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, Duration.ofMinutes(15), 100);
        CountDownLatch ready = new CountDownLatch(40);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 40; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        limiter.consume("203.0.113.8");
                        accepted.incrementAndGet();
                    } catch (ApiHttpException exception) {
                        assertEquals("RATE_LIMITED", exception.code());
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertEquals(5, accepted.get());
        assertEquals(35, rejected.get());
        assertEquals(1, limiter.trackedAddressCount());
    }

    @Test
    void expiredEntriesAreCleanedAndCapacityRemainsBounded() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-02T08:00:00Z"));
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, Duration.ofSeconds(60), 2, clock);

        limiter.consume("192.0.2.1");
        limiter.consume("192.0.2.2");
        assertThrows(ApiHttpException.class, () -> limiter.consume("192.0.2.3"));
        assertEquals(2, limiter.trackedAddressCount());

        clock.advance(Duration.ofSeconds(61));
        limiter.consume("192.0.2.3");
        assertEquals(1, limiter.trackedAddressCount());

        limiter.reset("192.0.2.3");
        assertEquals(0, limiter.trackedAddressCount());
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
