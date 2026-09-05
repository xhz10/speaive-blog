package com.speaive.blog.application.port.out.ai;

import java.util.List;
import java.util.function.Supplier;

/** AI 批次执行出站端口；并发技术与线程生命周期由适配器负责。 */
public interface AiGenerationTaskRunner {
    /**
     * 同一 key 同时只接纳一批，容量不足立即拒绝。准备工作成功后再并发执行任务，返回顺序与输入一致。
     * 准备工作和任务共用并发额度；返回或抛错前结束本批全部任务，不允许任务脱离请求继续运行。
     */
    <T> List<T> run(String key, Runnable prepare, List<Supplier<T>> tasks);
}
