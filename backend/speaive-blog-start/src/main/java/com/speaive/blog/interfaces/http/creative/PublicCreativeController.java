package com.speaive.blog.interfaces.http.creative;

import com.speaive.blog.application.port.in.creative.CreativeWorkspaceUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicCreativeController {
    private final CreativeWorkspaceUseCase creative;
    private final CreativeHttpMapper mapper;

    public PublicCreativeController(CreativeWorkspaceUseCase creative, CreativeHttpMapper mapper) {
        this.creative = creative;
        this.mapper = mapper;
    }

    @GetMapping("/works")
    CreativeResponses.WorkList works() {
        return mapper.toResponse(creative.listPublicWorks());
    }

    @GetMapping("/works/{slug}")
    CreativeResponses.WorkDetail work(@PathVariable String slug) {
        return mapper.toResponse(creative.getPublicWork(slug));
    }

    @GetMapping("/works/navigation")
    ResponseEntity<CreativeResponses.WorkNavigation> navigation(
            @RequestParam String contentType,
            @RequestParam String contentSlug,
            @RequestParam(required = false) String workSlug) {
        var result = creative.getPublicNavigation(contentType, contentSlug, workSlug);
        return result == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(mapper.toResponse(result));
    }

    @GetMapping("/shares/{token}")
    CreativeResponses.SharedContent share(@PathVariable String token) {
        return mapper.toResponse(creative.resolveShare(token));
    }

    @GetMapping("/posts/{slug}/discussion-digest")
    CreativeResponses.DiscussionDigest digest(@PathVariable String slug) {
        return mapper.toResponse(creative.getPublicDiscussionDigest(slug));
    }
}
