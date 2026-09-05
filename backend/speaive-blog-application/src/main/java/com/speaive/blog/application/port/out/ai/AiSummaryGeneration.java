package com.speaive.blog.application.port.out.ai;

/**
 * AI 记忆摘要生成结果，保存纯文本与可选的模型用量信息。
 */
public record AiSummaryGeneration(
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens
) {
}
