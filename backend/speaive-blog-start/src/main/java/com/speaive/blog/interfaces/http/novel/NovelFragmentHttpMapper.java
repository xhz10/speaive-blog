package com.speaive.blog.interfaces.http.novel;

import com.speaive.blog.application.command.novel.NovelFragmentWriteCommand;
import com.speaive.blog.application.result.novel.NovelFragmentDetailResult;
import com.speaive.blog.application.result.novel.NovelFragmentListResult;
import com.speaive.blog.application.result.novel.NovelFragmentSummaryResult;
import com.speaive.blog.application.result.post.AuthorResult;
import com.speaive.blog.interfaces.http.novel.NovelFragmentRequests.CreateNovelFragmentRequest;
import com.speaive.blog.interfaces.http.novel.NovelFragmentRequests.UpdateNovelFragmentRequest;
import com.speaive.blog.interfaces.http.novel.NovelFragmentResponses.AuthorSummary;
import com.speaive.blog.interfaces.http.novel.NovelFragmentResponses.NovelFragmentDetail;
import com.speaive.blog.interfaces.http.novel.NovelFragmentResponses.NovelFragmentList;
import com.speaive.blog.interfaces.http.novel.NovelFragmentResponses.NovelFragmentSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface NovelFragmentHttpMapper {
    NovelFragmentWriteCommand toCommand(CreateNovelFragmentRequest request);

    @Mapping(target = "slug", source = "slug")
    NovelFragmentWriteCommand toCommand(String slug, UpdateNovelFragmentRequest request);

    NovelFragmentDetail toResponse(NovelFragmentDetailResult result);

    NovelFragmentSummary toResponse(NovelFragmentSummaryResult result);

    AuthorSummary toResponse(AuthorResult result);

    NovelFragmentList toResponse(NovelFragmentListResult result);

}
