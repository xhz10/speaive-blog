package com.speaive.blog.infrastructure.ai;

import com.speaive.blog.application.error.BlogException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class VirtualThreadAiGenerationTaskRunnerTests {
    @Test
    void usesVirtualThreadsConcurrentlyAndKeepsInputOrder() throws Exception {
        var runner = new VirtualThreadAiGenerationTaskRunner(2, 4);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        Supplier<Integer> task = () -> {
            assertTrue(Thread.currentThread().isVirtual());
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new IllegalStateException(e); }
            finally { active.decrementAndGet(); }
            return 2;
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = executor.submit(() -> runner.run("post", () -> {}, List.of(task, task, () -> 3)));
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertEquals(2, peak.get());
                assertThrows(BlogException.class, () -> runner.run("post", () -> fail("重复批次不能执行"), List.of(() -> 0)));
                assertThrows(BlogException.class, () -> runner.run("other", () -> fail("满队列不能执行"), List.of(() -> 0, () -> 1)));
            } finally { release.countDown(); }
            assertEquals(List.of(2, 2, 3), result.get(3, TimeUnit.SECONDS));
        }
        assertEquals(List.of(9), runner.run("post", () -> {}, List.of(() -> 9)));
    }

    @Test
    void preparationAndFailureReleaseAdmissionAndExecutionPermits() {
        var runner = new VirtualThreadAiGenerationTaskRunner(1, 1);
        assertThrows(IllegalStateException.class, () -> runner.run("post", () -> { throw new IllegalStateException(); }, List.of(() -> 1)));
        assertThrows(IllegalStateException.class, () -> runner.run("post", () -> {}, List.of(() -> { throw new IllegalStateException(); })));
        assertEquals(List.of(5), runner.run("post", () -> {}, List.of(() -> 5)));
    }

    @Test
    void allBatchesShareTheSameConcurrencyBudget() throws Exception {
        var runner = new VirtualThreadAiGenerationTaskRunner(1, 2);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        Supplier<Integer> task = () -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new IllegalStateException(e); }
            finally { active.decrementAndGet(); }
            return 1;
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> runner.run("one", () -> {}, List.of(task)));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var second = executor.submit(() -> runner.run("two", () -> {
                peak.accumulateAndGet(active.incrementAndGet(), Math::max); active.decrementAndGet();
            }, List.of(task)));
            release.countDown();
            first.get(3, TimeUnit.SECONDS); second.get(3, TimeUnit.SECONDS);
            assertEquals(1, peak.get());
        }
    }

    @Test
    void interruptionCancelsChildrenAndLeavesRunnerReusable() throws Exception {
        var runner = new VirtualThreadAiGenerationTaskRunner(1, 1);
        var entered = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                assertThrows(BlogException.class, () -> runner.run("post", () -> {}, List.of(() -> {
                    entered.countDown();
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return 1;
                })));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { finished.countDown(); }
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        caller.interrupt();
        assertTrue(finished.await(3, TimeUnit.SECONDS));
        assertEquals(List.of(7), runner.run("post", () -> {}, List.of(() -> 7)));
    }
}
