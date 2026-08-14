package com.speaive.blog.interfaces.http.post;

import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.result.post.AuthorResult;
import com.speaive.blog.application.result.post.ContentScanErrorResult;
import com.speaive.blog.application.result.post.PostDetailResult;
import com.speaive.blog.application.result.post.PostListResult;
import com.speaive.blog.application.result.post.PostSummaryResult;
import com.speaive.blog.interfaces.http.post.PostRequests.CreatePostRequest;
import com.speaive.blog.interfaces.http.post.PostRequests.UpdatePostRequest;
import com.speaive.blog.interfaces.http.post.PostResponses.AuthorSummary;
import com.speaive.blog.interfaces.http.post.PostResponses.ContentScanError;
import com.speaive.blog.interfaces.http.post.PostResponses.PostDetail;
import com.speaive.blog.interfaces.http.post.PostResponses.PostList;
import com.speaive.blog.interfaces.http.post.PostResponses.PostSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface PostHttpMapper {
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

    @Named("emptyToNull")
    default String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
