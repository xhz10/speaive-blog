package com.speaive.blog.application.port.out.ai;

/**
 * AI 评论生成结果，包含纯文本、实际模型及服务商可选返回的 token 用量。
 */
public record AiCommentGeneration(
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens
) {
}
