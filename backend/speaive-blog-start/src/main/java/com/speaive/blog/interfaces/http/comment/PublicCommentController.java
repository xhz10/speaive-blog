package com.speaive.blog.interfaces.http.comment;

import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.interfaces.http.comment.CommentResponses.CommentList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/posts/{slug}/comments")
public class PublicCommentController {
    private final CommentUseCase comments;
    private final CommentHttpMapper mapper;

    public PublicCommentController(CommentUseCase comments, CommentHttpMapper mapper) {
        this.comments = comments;
        this.mapper = mapper;
    }

    @GetMapping
    CommentList list(@PathVariable String slug) {
        return mapper.toResponse(comments.listPublishedComments(slug));
    }
}
