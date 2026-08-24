package com.speaive.blog.interfaces.http.comment;

import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.interfaces.http.comment.CommentRequests.GenerateAiCommentRequest;
import com.speaive.blog.interfaces.http.comment.CommentResponses.CommentDetail;
import com.speaive.blog.interfaces.http.comment.CommentResponses.CommentList;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studio")
public class StudioCommentController {
    private final CommentUseCase comments;
    private final CommentHttpMapper mapper;

    public StudioCommentController(CommentUseCase comments, CommentHttpMapper mapper) {
        this.comments = comments;
        this.mapper = mapper;
    }

    @GetMapping("/posts/{slug}/comments")
    CommentList list(@PathVariable String slug) {
        return mapper.toResponse(comments.listStudioComments(slug));
    }

    @PostMapping("/posts/{slug}/ai-comments")
    CommentDetail generate(@PathVariable String slug, @Valid @RequestBody GenerateAiCommentRequest request) {
        return mapper.toResponse(comments.generateAiComment(slug, request.agentId()));
    }

    @PostMapping("/comments/{id}/publish")
    CommentDetail publish(@PathVariable String id) {
        return mapper.toResponse(comments.publishComment(id));
    }

    @PostMapping("/comments/{id}/hide")
    CommentDetail hide(@PathVariable String id) {
        return mapper.toResponse(comments.hideComment(id));
    }
}
