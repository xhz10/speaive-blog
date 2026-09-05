package com.speaive.blog.infrastructure.ai;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.ai.AiGenerationTaskRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Java 25 虚拟线程执行器：等待远程模型时不独占平台线程；信号量限制供应商请求并发。
 * 容量及同文章互斥作用于当前进程的批量入口；单角色和定时生成仍使用各自原有流程。
 */
public final class VirtualThreadAiGenerationTaskRunner implements AiGenerationTaskRunner {
    private final Semaphore slots;
    private final Semaphore capacity;
    private final Set<String> activeKeys = ConcurrentHashMap.newKeySet();

    public VirtualThreadAiGenerationTaskRunner(int concurrency, int capacity) {
        if (concurrency < 1 || concurrency > 32 || capacity < concurrency || capacity > 1000) {
            throw new IllegalArgumentException("批量并发数须为 1 至 32，容量须介于并发数与 1000 之间");
        }
        this.slots = new Semaphore(concurrency, true);
        this.capacity = new Semaphore(capacity, true);
    }

    @Override
    public <T> List<T> run(String key, Runnable prepare, List<Supplier<T>> tasks) {
        List<Supplier<T>> submitted = List.copyOf(tasks);
        if (submitted.isEmpty()) return List.of();
        if (!activeKeys.add(key)) throw busy("这篇文章已有批量生成任务，请等待完成");
        boolean admitted = false;
        try {
            admitted = capacity.tryAcquire(submitted.size());
            if (!admitted) throw busy("生成队列已满，请稍后重试");
            withSlot(() -> { prepare.run(); return null; });
            // 每个任务使用独立虚拟线程；关闭作用域时等待所有子任务，避免后台遗留任务。
            try (var executor = Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("blog-ai-comment-", 0).factory())) {
                List<Future<T>> futures = new ArrayList<>();
                try {
                    for (var task : submitted) futures.add(executor.submit(() -> withSlot(task)));
                    List<T> results = new ArrayList<>();
                    for (var future : futures) results.add(future.get());
                    return List.copyOf(results);
                } catch (InterruptedException e) {
                    futures.forEach(future -> future.cancel(true));
                    Thread.currentThread().interrupt();
                    throw busy("批量生成已中断，请查看已生成的评论后重试");
                } catch (ExecutionException e) {
                    futures.forEach(future -> future.cancel(true));
                    if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                    throw new IllegalStateException("批量生成任务异常", e.getCause());
                }
            }
        } finally {
            if (admitted) capacity.release(submitted.size());
            activeKeys.remove(key);
        }
    }

    private <T> T withSlot(Supplier<T> action) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw busy("等待生成额度时被中断");
        }
        try { return action.get(); }
        finally { slots.release(); }
    }

    private static BlogException busy(String message) {
        return new BlogException(BlogErrorCode.GENERATION_CONFLICT, message);
    }
}
