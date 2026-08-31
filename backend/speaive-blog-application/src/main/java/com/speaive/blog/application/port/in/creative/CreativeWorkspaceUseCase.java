package com.speaive.blog.application.port.in.creative;

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

public interface CreativeWorkspaceUseCase {
    InspirationListResult listInspirations();
    InspirationResult createInspiration(InspirationWriteCommand command);
    InspirationResult updateInspiration(String id, long revision, InspirationWriteCommand command);
    InspirationResult transitionInspiration(String id, InspirationTransitionCommand command);

    ContentRevisionListResult listRevisions(String contentType, String slug);
    ContentRevisionListResult restoreRevision(String contentType, String slug, long revision, String currentVersion);

    WorkCollectionListResult listStudioWorks();
    WorkCollectionListResult listPublicWorks();
    WorkCollectionResult getStudioWork(String slug);
    WorkCollectionResult getPublicWork(String slug);
    WorkCollectionResult createWork(WorkCollectionWriteCommand command);
    WorkCollectionResult updateWork(String slug, long revision, WorkCollectionWriteCommand command);
    WorkNavigationResult getPublicNavigation(String contentType, String contentSlug, String workSlug);

    ShareGrantListResult listShares(String contentType, String contentSlug);
    ShareGrantResult createShare(CreateShareCommand command);
    ShareGrantResult revokeShare(String id);
    SharedContentResult resolveShare(String token);

    EditorialReviewListResult listEditorialReviews(String postSlug);
    EditorialReviewResult generateEditorialReview(String postSlug, GenerateEditorialReviewCommand command);

    DiscussionDigestResult getStudioDiscussionDigest(String postSlug);
    DiscussionDigestResult getPublicDiscussionDigest(String postSlug);
    DiscussionDigestResult generateDiscussionDigest(String postSlug);

    ResurfacingResult getResurfacingSuggestions();
}
