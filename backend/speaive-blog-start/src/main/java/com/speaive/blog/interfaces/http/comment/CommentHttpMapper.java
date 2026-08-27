package com.speaive.blog.interfaces.http.comment;

import com.speaive.blog.application.result.comment.CommentListResult;
import com.speaive.blog.application.result.comment.CommentResult;
import com.speaive.blog.application.result.comment.AiSummaryCoverageResult;
import com.speaive.blog.application.result.comment.PostAiSummaryResult;
import com.speaive.blog.application.result.post.AuthorResult;
import com.speaive.blog.interfaces.http.comment.CommentResponses.CommentAuthor;
import com.speaive.blog.interfaces.http.comment.CommentResponses.CommentDetail;
import com.speaive.blog.interfaces.http.comment.CommentResponses.CommentList;
import com.speaive.blog.interfaces.http.comment.CommentResponses.AiSummaryCoverage;
import com.speaive.blog.interfaces.http.comment.CommentResponses.PostAiSummaryDetail;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface CommentHttpMapper {
    CommentDetail toResponse(CommentResult result);

    CommentAuthor toResponse(AuthorResult result);

    CommentList toResponse(CommentListResult result);

    PostAiSummaryDetail toResponse(PostAiSummaryResult result);

    AiSummaryCoverage toResponse(AiSummaryCoverageResult result);
}
