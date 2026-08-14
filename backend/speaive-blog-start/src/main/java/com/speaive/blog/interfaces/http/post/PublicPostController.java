package com.speaive.blog.interfaces.http.post;

import com.speaive.blog.application.port.in.post.PostUseCase;
import com.speaive.blog.interfaces.http.post.PostResponses.PostDetail;
import com.speaive.blog.interfaces.http.post.PostResponses.PostList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/posts")
public class PublicPostController {
    private final PostUseCase posts;
    private final PostHttpMapper mapper;

    public PublicPostController(PostUseCase posts, PostHttpMapper mapper) {
        this.posts = posts;
        this.mapper = mapper;
    }

    @GetMapping
    PostList list() {
        return mapper.toResponse(posts.listPublishedPosts());
    }

    @GetMapping("/{slug}")
    PostDetail get(@PathVariable String slug) {
        return mapper.toResponse(posts.getPublishedPost(slug));
    }
}
