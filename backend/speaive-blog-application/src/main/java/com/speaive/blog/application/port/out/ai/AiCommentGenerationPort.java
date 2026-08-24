package com.speaive.blog.application.port.out.ai;

public interface AiCommentGenerationPort {
    boolean isAvailable();

    AiCommentGeneration generate(AiCommentPrompt prompt);
}
