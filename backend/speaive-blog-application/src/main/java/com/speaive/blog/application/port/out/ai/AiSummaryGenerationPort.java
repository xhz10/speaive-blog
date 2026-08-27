package com.speaive.blog.application.port.out.ai;

public interface AiSummaryGenerationPort {
    boolean isAvailable();

    AiSummaryGeneration generate(AiSummaryPrompt prompt);
}
