package com.speaive.blog.application.port.out.ai;

import java.util.List;

/**
 * 文章记忆摘要的模型输入，按一次文章读取快照提供标题、标签与正文。
 */
public record AiSummaryPrompt(
        String title,
        String description,
        List<String> tags,
        String body
) {
    public AiSummaryPrompt {
        tags = List.copyOf(tags);
    }
}
