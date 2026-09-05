package com.speaive.blog.application.port.out.ai;

/**
 * AI 评论生成出站端口；模型实现返回纯文本和可用用量元数据，不得自行发布评论或扩大读取权限。
 */
public interface AiCommentGenerationPort {
    boolean isAvailable();

    AiCommentGeneration generate(AiCommentPrompt prompt);
}
