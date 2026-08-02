package com.speaive.blog.infrastructure.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugLockRegistryTests {

    @Test
    void oneSlugKeepsOneLockAcrossQueuedAndLaterCallers() throws Exception {
        SlugLockRegistry registry = new SlugLockRegistry();
        CountDownLatch ready = new CountDownLatch(80);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 80; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    registry.withLock("same-slug", () -> {
                        int current = active.incrementAndGet();
                        maximumActive.accumulateAndGet(current, Math::max);
                        LockSupport.parkNanos(200_000);
                        active.decrementAndGet();
                        return null;
                    });
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(() -> registry.withLock("same-slug", () -> null)));
            }
            for (int index = 80; index < futures.size(); index++) {
                futures.get(index).get();
            }
        }

        assertEquals(1, maximumActive.get());
        assertEquals(1, registry.trackedSlugCount());
    }
}
