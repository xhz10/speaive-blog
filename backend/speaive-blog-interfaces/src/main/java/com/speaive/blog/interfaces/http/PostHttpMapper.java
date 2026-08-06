package com.speaive.blog.interfaces.http;

import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.application.result.AuthorResult;
import com.speaive.blog.application.result.ContentScanErrorResult;
import com.speaive.blog.application.result.PostDetailResult;
import com.speaive.blog.application.result.PostListResult;
import com.speaive.blog.application.result.PostSummaryResult;
import com.speaive.blog.application.result.StoredMediaResult;
import com.speaive.blog.interfaces.http.PostRequests.CreatePostRequest;
import com.speaive.blog.interfaces.http.PostRequests.UpdatePostRequest;
import com.speaive.blog.interfaces.http.PostResponses.AuthorSummary;
import com.speaive.blog.interfaces.http.PostResponses.ContentScanError;
import com.speaive.blog.interfaces.http.PostResponses.PostDetail;
import com.speaive.blog.interfaces.http.PostResponses.PostList;
import com.speaive.blog.interfaces.http.PostResponses.PostSummary;
import com.speaive.blog.interfaces.http.PostResponses.StoredMediaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface PostHttpMapper {
    @Mapping(target = "cover", source = "cover", qualifiedByName = "emptyToNull")
    PostWriteCommand toCommand(CreatePostRequest request);

    @Mapping(target = "slug", source = "slug")
    @Mapping(target = "cover", source = "request.cover", qualifiedByName = "emptyToNull")
    PostWriteCommand toCommand(String slug, UpdatePostRequest request);

    PostDetail toResponse(PostDetailResult result);

    PostSummary toResponse(PostSummaryResult result);

    AuthorSummary toResponse(AuthorResult result);

    ContentScanError toResponse(ContentScanErrorResult result);

    PostList toResponse(PostListResult result);

    StoredMediaResponse toResponse(StoredMediaResult result);

    @Named("emptyToNull")
    default String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
