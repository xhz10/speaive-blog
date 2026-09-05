package com.speaive.blog.application.port.out.ai;

/**
 * 文章 AI 记忆摘要生成端口，模型实现只处理已由用例授权的输入。
 */
public interface AiSummaryGenerationPort {
    boolean isAvailable();

    AiSummaryGeneration generate(AiSummaryPrompt prompt);
}
