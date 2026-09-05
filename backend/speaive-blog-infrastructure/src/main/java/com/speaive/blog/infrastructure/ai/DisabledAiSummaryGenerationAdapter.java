package com.speaive.blog.infrastructure.ai;

import com.speaive.blog.application.port.out.ai.AiSummaryGeneration;
import com.speaive.blog.application.port.out.ai.AiSummaryGenerationPort;
import com.speaive.blog.application.port.out.ai.AiSummaryPrompt;

/**
 * AI 摘要关闭时的适配器，保持端口存在并明确报告功能不可用。
 */
public final class DisabledAiSummaryGenerationAdapter implements AiSummaryGenerationPort {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public AiSummaryGeneration generate(AiSummaryPrompt prompt) {
        throw new IllegalStateException("AI 摘要服务尚未配置");
    }
}
