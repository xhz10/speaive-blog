package com.speaive.blog.interfaces.http.creative;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.creative.CreativeWorkspaceUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studio/creative")
public class StudioCreativeController {
    private final CreativeWorkspaceUseCase creative;
    private final CreativeHttpMapper mapper;

    public StudioCreativeController(CreativeWorkspaceUseCase creative, CreativeHttpMapper mapper) {
        this.creative = creative;
        this.mapper = mapper;
    }

    @GetMapping("/inspirations")
    CreativeResponses.InspirationList inspirations() {
        return mapper.toResponse(creative.listInspirations());
    }

    @PostMapping("/inspirations")
    @ResponseStatus(HttpStatus.CREATED)
    CreativeResponses.InspirationDetail createInspiration(
            @Valid @RequestBody CreativeRequests.InspirationWriteRequest request) {
        return mapper.toResponse(creative.createInspiration(mapper.toCommand(request)));
    }

    @PutMapping("/inspirations/{id}")
    CreativeResponses.InspirationDetail updateInspiration(
            @PathVariable String id,
            @Valid @RequestBody CreativeRequests.InspirationWriteRequest request) {
        if (request.revision() == null) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "revision 不能为空");
        }
        return mapper.toResponse(creative.updateInspiration(id, request.revision(), mapper.toCommand(request)));
    }

    @PostMapping("/inspirations/{id}/transition")
    CreativeResponses.InspirationDetail transitionInspiration(
            @PathVariable String id,
            @Valid @RequestBody CreativeRequests.InspirationTransitionRequest request) {
        return mapper.toResponse(creative.transitionInspiration(id, mapper.toCommand(request)));
    }

    @GetMapping("/{contentType}/{slug}/revisions")
    CreativeResponses.ContentRevisionList revisions(
            @PathVariable String contentType, @PathVariable String slug) {
        return mapper.toResponse(creative.listRevisions(contentType, slug));
    }

    @PostMapping("/{contentType}/{slug}/revisions/{revision}/restore")
    CreativeResponses.ContentRevisionList restore(
            @PathVariable String contentType,
            @PathVariable String slug,
            @PathVariable long revision,
            @Valid @RequestBody CreativeRequests.RestoreRevisionRequest request) {
        return mapper.toResponse(creative.restoreRevision(contentType, slug, revision, request.currentVersion()));
    }

    @GetMapping("/works")
    CreativeResponses.WorkList works() {
        return mapper.toResponse(creative.listStudioWorks());
    }

    @GetMapping("/works/{slug}")
    CreativeResponses.WorkDetail work(@PathVariable String slug) {
        return mapper.toResponse(creative.getStudioWork(slug));
    }

    @PostMapping("/works")
    @ResponseStatus(HttpStatus.CREATED)
    CreativeResponses.WorkDetail createWork(@Valid @RequestBody CreativeRequests.WorkWriteRequest request) {
        return mapper.toResponse(creative.createWork(mapper.toCommand(request)));
    }

    @PutMapping("/works/{slug}")
    CreativeResponses.WorkDetail updateWork(
            @PathVariable String slug, @Valid @RequestBody CreativeRequests.WorkWriteRequest request) {
        if (request.revision() == null) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "revision 不能为空");
        }
        return mapper.toResponse(creative.updateWork(slug, request.revision(), mapper.toCommand(request)));
    }

    @GetMapping("/shares")
    CreativeResponses.ShareList shares(
            @RequestParam String contentType, @RequestParam String contentSlug) {
        return mapper.toResponse(creative.listShares(contentType, contentSlug));
    }

    @PostMapping("/shares")
    @ResponseStatus(HttpStatus.CREATED)
    CreativeResponses.ShareDetail createShare(@Valid @RequestBody CreativeRequests.CreateShareRequest request) {
        return mapper.toResponse(creative.createShare(mapper.toCommand(request)));
    }

    @PostMapping("/shares/{id}/revoke")
    CreativeResponses.ShareDetail revokeShare(@PathVariable String id) {
        return mapper.toResponse(creative.revokeShare(id));
    }

    @GetMapping("/posts/{slug}/reviews")
    CreativeResponses.EditorialReviewList reviews(@PathVariable String slug) {
        return mapper.toResponse(creative.listEditorialReviews(slug));
    }

    @PostMapping("/posts/{slug}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    CreativeResponses.EditorialReviewDetail generateReview(
            @PathVariable String slug,
            @Valid @RequestBody CreativeRequests.EditorialReviewRequest request) {
        return mapper.toResponse(creative.generateEditorialReview(slug, mapper.toCommand(request)));
    }

    @GetMapping("/posts/{slug}/discussion-digest")
    CreativeResponses.DiscussionDigest digest(@PathVariable String slug) {
        return mapper.toResponse(creative.getStudioDiscussionDigest(slug));
    }

    @PostMapping("/posts/{slug}/discussion-digest")
    CreativeResponses.DiscussionDigest generateDigest(@PathVariable String slug) {
        return mapper.toResponse(creative.generateDiscussionDigest(slug));
    }

    @GetMapping("/resurfacing")
    CreativeResponses.Resurfacing resurfacing() {
        return mapper.toResponse(creative.getResurfacingSuggestions());
    }
}
