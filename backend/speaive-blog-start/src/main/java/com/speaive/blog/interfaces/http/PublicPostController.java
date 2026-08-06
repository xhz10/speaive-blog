package com.speaive.blog.interfaces.http;

import com.speaive.blog.application.port.in.BlogUseCase;
import com.speaive.blog.interfaces.http.PostResponses.PostDetail;
import com.speaive.blog.interfaces.http.PostResponses.PostList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/posts")
public class PublicPostController {
    private final BlogUseCase blog;
    private final PostHttpMapper mapper;

    public PublicPostController(BlogUseCase blog, PostHttpMapper mapper) {
        this.blog = blog;
        this.mapper = mapper;
    }

    @GetMapping
    PostList list() {
        return mapper.toResponse(blog.listPublishedPosts());
    }

    @GetMapping("/{slug}")
    PostDetail get(@PathVariable String slug) {
        return mapper.toResponse(blog.getPublishedPost(slug));
    }
}
