package com.speaive.blog.application.port.in.comment;

import com.speaive.blog.application.result.comment.CommentListResult;
import com.speaive.blog.application.result.comment.CommentResult;

public interface CommentUseCase {
    CommentListResult listStudioComments(String postSlug);

    CommentListResult listPublishedComments(String postSlug);

    CommentResult generateAiComment(String postSlug, String agentId);

    CommentResult publishComment(String commentId);

    CommentResult hideComment(String commentId);
}
