package com.speaive.blog.interfaces.http.novel;

import com.speaive.blog.application.port.in.novel.NovelFragmentUseCase;
import com.speaive.blog.interfaces.http.novel.NovelFragmentRequests.CreateNovelFragmentRequest;
import com.speaive.blog.interfaces.http.novel.NovelFragmentRequests.UpdateNovelFragmentRequest;
import com.speaive.blog.interfaces.http.novel.NovelFragmentRequests.VersionRequest;
import com.speaive.blog.interfaces.http.novel.NovelFragmentResponses.NovelFragmentDetail;
import com.speaive.blog.interfaces.http.novel.NovelFragmentResponses.NovelFragmentList;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studio/novels")
public class StudioNovelFragmentController {
    private final NovelFragmentUseCase fragments;
    private final NovelFragmentHttpMapper mapper;

    public StudioNovelFragmentController(NovelFragmentUseCase fragments, NovelFragmentHttpMapper mapper) {
        this.fragments = fragments;
        this.mapper = mapper;
    }

    @GetMapping
    NovelFragmentList list() {
        return mapper.toResponse(fragments.listStudioFragments());
    }

    @GetMapping("/{slug}")
    NovelFragmentDetail get(@PathVariable String slug) {
        return mapper.toResponse(fragments.getStudioFragment(slug));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NovelFragmentDetail create(@Valid @RequestBody CreateNovelFragmentRequest request) {
        return mapper.toResponse(fragments.createDraft(mapper.toCommand(request)));
    }

    @PutMapping("/{slug}")
    NovelFragmentDetail update(
            @PathVariable String slug,
            @Valid @RequestBody UpdateNovelFragmentRequest request) {
        return mapper.toResponse(fragments.update(
                slug, request.version(), mapper.toCommand(slug, request)));
    }

    @PostMapping("/{slug}/publish")
    NovelFragmentDetail publish(@PathVariable String slug, @Valid @RequestBody VersionRequest request) {
        return mapper.toResponse(fragments.publish(slug, request.version()));
    }

    @PostMapping("/{slug}/unpublish")
    NovelFragmentDetail unpublish(@PathVariable String slug, @Valid @RequestBody VersionRequest request) {
        return mapper.toResponse(fragments.unpublish(slug, request.version()));
    }
}
