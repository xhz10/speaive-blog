package com.speaive.blog.domain.creative;

import java.time.Instant;
import java.util.Objects;

/**
 * 发布前的私密 AI 编辑意见，绑定文章修订与可选原文引用。它只进入站长编辑室，不参与公开评论审核状态机。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param postId 所属文章的稳定 ID
 * @param postRevision 生成或快照所依据的文章修订号
 * @param agentId 负责生成或执行任务的 Agent ID
 * @param quoteText 作者选中的原文；审阅全文时可为空
 * @param quotePrefix 选中文本之前的上下文，用于定位引用
 * @param quoteSuffix 选中文本之后的上下文，用于定位引用
 * @param body 正文内容；格式与长度由当前业务类型约束
 * @param model 实际或指定的模型名称；未提供时可为空
 * @param inputTokens 服务商返回的输入 token 数；为空表示未提供，不能当作零
 * @param outputTokens 服务商返回的输出 token 数；为空表示未提供，不能当作零
 * @param createdAt 首次创建时间
 */
public record EditorialReview(
        String id,
        String postId,
        long postRevision,
        String agentId,
        String quoteText,
        String quotePrefix,
        String quoteSuffix,
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt
) {
    public EditorialReview {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(createdAt, "createdAt");
        if (postRevision < 1) throw new IllegalArgumentException("postRevision 必须大于 0");
    }
}
