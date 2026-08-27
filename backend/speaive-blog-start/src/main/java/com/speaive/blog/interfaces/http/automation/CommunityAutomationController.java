package com.speaive.blog.interfaces.http.automation;

import com.speaive.blog.application.port.in.automation.CommunityAutomationUseCase;
import com.speaive.blog.application.result.automation.PostCommunityPolicyResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studio/posts/{slug}/community-agents")
public class CommunityAutomationController {
    private final CommunityAutomationUseCase automation;

    public CommunityAutomationController(CommunityAutomationUseCase automation) {
        this.automation = automation;
    }

    @GetMapping
    PostCommunityPolicyResult get(@PathVariable String slug) {
        return automation.getPostPolicy(slug);
    }

    @PutMapping
    PostCommunityPolicyResult update(
            @PathVariable String slug,
            @Valid @RequestBody UpdatePolicyRequest request) {
        return automation.updatePostPolicy(slug, request.enabled(), request.version());
    }

    record UpdatePolicyRequest(
            boolean enabled,
            @Min(value = 0, message = "策略版本不能小于 0") long version
    ) {
    }
}
