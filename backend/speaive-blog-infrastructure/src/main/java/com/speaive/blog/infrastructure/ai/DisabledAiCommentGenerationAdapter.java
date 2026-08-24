package com.speaive.blog.infrastructure.ai;

import com.speaive.blog.application.port.out.ai.AiCommentGeneration;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.ai.AiCommentPrompt;

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
