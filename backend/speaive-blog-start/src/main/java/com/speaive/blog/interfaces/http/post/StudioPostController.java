package com.speaive.blog.interfaces.http.post;

import com.speaive.blog.application.port.in.post.PostUseCase;
import com.speaive.blog.interfaces.http.post.PostRequests.CreatePostRequest;
import com.speaive.blog.interfaces.http.post.PostRequests.UpdatePostRequest;
import com.speaive.blog.interfaces.http.post.PostRequests.VersionRequest;
import com.speaive.blog.interfaces.http.post.PostResponses.PostDetail;
import com.speaive.blog.interfaces.http.post.PostResponses.PostList;
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
@RequestMapping("/api/v1/studio")
public class StudioPostController {
    private final PostUseCase posts;
    private final PostHttpMapper mapper;

    public StudioPostController(PostUseCase posts, PostHttpMapper mapper) {
        this.posts = posts;
        this.mapper = mapper;
    }

    @GetMapping("/posts")
    PostList list() {
        return mapper.toResponse(posts.listStudioPosts());
    }

    @GetMapping("/posts/{slug}")
    PostDetail get(@PathVariable String slug) {
        return mapper.toResponse(posts.getStudioPost(slug));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    PostDetail create(@Valid @RequestBody CreatePostRequest request) {
        return mapper.toResponse(posts.createDraft(mapper.toCommand(request)));
    }

    @PutMapping("/posts/{slug}")
    PostDetail update(@PathVariable String slug, @Valid @RequestBody UpdatePostRequest request) {
        return mapper.toResponse(posts.update(slug, request.version(), mapper.toCommand(slug, request)));
    }

    @PostMapping("/posts/{slug}/publish")
    PostDetail publish(@PathVariable String slug, @Valid @RequestBody VersionRequest request) {
        return mapper.toResponse(posts.publish(slug, request.version()));
    }

    @PostMapping("/posts/{slug}/unpublish")
    PostDetail unpublish(@PathVariable String slug, @Valid @RequestBody VersionRequest request) {
        return mapper.toResponse(posts.unpublish(slug, request.version()));
    }

    @PostMapping("/posts/{slug}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String slug, @Valid @RequestBody VersionRequest request) {
        posts.archive(slug, request.version());
    }
}
