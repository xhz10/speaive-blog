package com.speaive.blog.application.port.in.comment;

import com.speaive.blog.application.command.comment.GenerateCommentBatchCommand;
import com.speaive.blog.application.result.comment.CommentBatchResult;

/** 管理员批量生成评论入口；各角色独立生成、独立提交，评论仍需审核。 */
public interface CommentBatchUseCase {
    CommentBatchResult generate(String postSlug, GenerateCommentBatchCommand command);
}
