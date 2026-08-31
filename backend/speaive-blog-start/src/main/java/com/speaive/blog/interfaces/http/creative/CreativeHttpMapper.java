package com.speaive.blog.interfaces.http.creative;

import com.speaive.blog.application.command.creative.CreateShareCommand;
import com.speaive.blog.application.command.creative.GenerateEditorialReviewCommand;
import com.speaive.blog.application.command.creative.InspirationTransitionCommand;
import com.speaive.blog.application.command.creative.InspirationWriteCommand;
import com.speaive.blog.application.command.creative.WorkCollectionWriteCommand;
import com.speaive.blog.application.result.creative.ContentRevisionListResult;
import com.speaive.blog.application.result.creative.DiscussionDigestResult;
import com.speaive.blog.application.result.creative.EditorialReviewListResult;
import com.speaive.blog.application.result.creative.EditorialReviewResult;
import com.speaive.blog.application.result.creative.InspirationListResult;
import com.speaive.blog.application.result.creative.InspirationResult;
import com.speaive.blog.application.result.creative.ResurfacingResult;
import com.speaive.blog.application.result.creative.ShareGrantListResult;
import com.speaive.blog.application.result.creative.ShareGrantResult;
import com.speaive.blog.application.result.creative.SharedContentResult;
import com.speaive.blog.application.result.creative.WorkCollectionListResult;
import com.speaive.blog.application.result.creative.WorkCollectionResult;
import com.speaive.blog.application.result.creative.WorkNavigationResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CreativeHttpMapper {
    InspirationWriteCommand toCommand(CreativeRequests.InspirationWriteRequest request);
    InspirationTransitionCommand toCommand(CreativeRequests.InspirationTransitionRequest request);
    WorkCollectionWriteCommand toCommand(CreativeRequests.WorkWriteRequest request);
    CreateShareCommand toCommand(CreativeRequests.CreateShareRequest request);
    GenerateEditorialReviewCommand toCommand(CreativeRequests.EditorialReviewRequest request);

    CreativeResponses.InspirationDetail toResponse(InspirationResult result);
    CreativeResponses.InspirationList toResponse(InspirationListResult result);
    CreativeResponses.ContentRevisionList toResponse(ContentRevisionListResult result);
    CreativeResponses.WorkDetail toResponse(WorkCollectionResult result);
    CreativeResponses.WorkList toResponse(WorkCollectionListResult result);
    CreativeResponses.WorkNavigation toResponse(WorkNavigationResult result);
    CreativeResponses.ShareDetail toResponse(ShareGrantResult result);
    CreativeResponses.ShareList toResponse(ShareGrantListResult result);
    CreativeResponses.SharedContent toResponse(SharedContentResult result);
    CreativeResponses.EditorialReviewDetail toResponse(EditorialReviewResult result);
    CreativeResponses.EditorialReviewList toResponse(EditorialReviewListResult result);
    CreativeResponses.DiscussionDigest toResponse(DiscussionDigestResult result);
    CreativeResponses.Resurfacing toResponse(ResurfacingResult result);

}
