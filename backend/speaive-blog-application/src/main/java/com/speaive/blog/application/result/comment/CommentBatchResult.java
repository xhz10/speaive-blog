package com.speaive.blog.application.result.comment;

import java.util.List;

/** 批次结果按请求顺序返回；某个角色失败不会回滚其他角色已生成的候选评论。 */
public record CommentBatchResult(List<Item> items) {
    public CommentBatchResult { items = List.copyOf(items); }

    /** comment 非空表示成功；失败时由 errorCode、errorMessage 说明原因。 */
    public record Item(String agentId, CommentResult comment, String errorCode, String errorMessage) {}
}
