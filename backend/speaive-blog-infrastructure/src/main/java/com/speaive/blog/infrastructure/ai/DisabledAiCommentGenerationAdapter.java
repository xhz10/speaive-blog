package com.speaive.blog.infrastructure.ai;

import com.speaive.blog.application.port.out.ai.AiCommentGeneration;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiCommentPrompt;

/**
 * AI 评论关闭时的适配器，明确报告不可用，避免访客刷新页面或未配置环境意外调用模型。
 */
public final class DisabledAiCommentGenerationAdapter implements AiCommentGenerationPort {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public AiCommentGeneration generate(AiCommentPrompt prompt) {
        throw new IllegalStateException("AI 评论服务尚未配置");
    }
}
