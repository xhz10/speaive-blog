package com.speaive.blog.interfaces.http;

import com.speaive.blog.application.BlogApplicationService;
import com.speaive.blog.interfaces.http.PostResponses.PostDetail;
import com.speaive.blog.interfaces.http.PostResponses.PostList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/posts")
public class PublicPostController {
    private final BlogApplicationService blog;

    public PublicPostController(BlogApplicationService blog) {
        this.blog = blog;
    }

    @GetMapping
    PostList list() {
        return PostResponses.list(blog.listPublishedPosts());
    }

    @GetMapping("/{slug}")
    PostDetail get(@PathVariable String slug) {
        return PostResponses.detail(blog.getPublishedPost(slug));
    }
}
