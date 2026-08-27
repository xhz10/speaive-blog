package com.speaive.blog.application.port.in.comment;

import com.speaive.blog.application.result.comment.AiSummaryCoverageResult;
import com.speaive.blog.application.result.comment.CommentListResult;
import com.speaive.blog.application.result.comment.CommentResult;
import com.speaive.blog.application.result.comment.PostAiSummaryResult;

public interface CommentUseCase {
    CommentListResult listStudioComments(String postSlug);

    CommentListResult listPublishedComments(String postSlug);

    CommentResult generateAiComment(String postSlug, String agentId);

    CommentResult generateAiReply(String commentId, String agentId);

    CommentResult generateAutomatedAiComment(String postId, long postRevision, String agentId);

    PostAiSummaryResult getAiSummary(String postSlug);

    PostAiSummaryResult generateAiSummary(String postSlug);

    AiSummaryCoverageResult getAiSummaryCoverage();

    AiSummaryCoverageResult backfillNextAiSummary();

    CommentResult publishComment(String commentId);

    CommentResult hideComment(String commentId);
}
