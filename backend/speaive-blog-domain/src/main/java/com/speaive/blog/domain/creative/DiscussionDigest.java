package com.speaive.blog.domain.creative;

import java.time.Instant;
import java.util.Objects;

/**
 * Agent 圆桌讨论摘要，绑定文章修订和评论指纹。任一来源发生变化时不能继续作为当前公开摘要展示。
 *
 * @param postId 所属文章的稳定 ID
 * @param postRevision 生成或快照所依据的文章修订号
 * @param commentsFingerprint 生成摘要时的评论内容指纹，用于判断讨论是否已变化
 * @param body 正文内容；格式与长度由当前业务类型约束
 * @param model 实际或指定的模型名称；未提供时可为空
 * @param inputTokens 服务商返回的输入 token 数；为空表示未提供，不能当作零
 * @param outputTokens 服务商返回的输出 token 数；为空表示未提供，不能当作零
 * @param updatedAt 最近一次修改或状态变化时间
 */
public record DiscussionDigest(
        String postId,
        long postRevision,
        String commentsFingerprint,
        String body,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant updatedAt
) {
    public DiscussionDigest {
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(commentsFingerprint, "commentsFingerprint");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (postRevision < 1) throw new IllegalArgumentException("postRevision 必须大于 0");
    }
}
