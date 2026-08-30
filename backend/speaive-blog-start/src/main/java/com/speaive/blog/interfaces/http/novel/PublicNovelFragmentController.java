package com.speaive.blog.interfaces.http.novel;

import com.speaive.blog.application.port.in.novel.NovelFragmentUseCase;
import com.speaive.blog.interfaces.http.novel.NovelFragmentResponses.NovelFragmentDetail;
import com.speaive.blog.interfaces.http.novel.NovelFragmentResponses.NovelFragmentList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/novels")
public class PublicNovelFragmentController {
    private final NovelFragmentUseCase fragments;
    private final NovelFragmentHttpMapper mapper;

    public PublicNovelFragmentController(NovelFragmentUseCase fragments, NovelFragmentHttpMapper mapper) {
        this.fragments = fragments;
        this.mapper = mapper;
    }

    @GetMapping
    NovelFragmentList list() {
        return mapper.toResponse(fragments.listPublishedFragments());
    }

    @GetMapping("/{slug}")
    NovelFragmentDetail get(@PathVariable String slug) {
        return mapper.toResponse(fragments.getPublishedFragment(slug));
    }
}
