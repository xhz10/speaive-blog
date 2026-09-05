package com.speaive.blog.interfaces.http.comment;

import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.application.port.in.comment.CommentBatchUseCase;
import com.speaive.blog.interfaces.http.comment.CommentRequests.GenerateAiCommentRequest;
import com.speaive.blog.interfaces.http.comment.CommentResponses.CommentDetail;
import com.speaive.blog.interfaces.http.comment.CommentResponses.CommentList;
import com.speaive.blog.interfaces.http.comment.CommentResponses.AiSummaryCoverage;
import com.speaive.blog.interfaces.http.comment.CommentResponses.PostAiSummaryDetail;
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
    private final CommentBatchUseCase batches;

    public StudioCommentController(CommentUseCase comments, CommentHttpMapper mapper, CommentBatchUseCase batches) {
        this.comments = comments;
        this.batches = batches;
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

    /** 批量生成仍由 /studio 的管理员认证和 CSRF 防护保护。 */
    @PostMapping("/posts/{slug}/ai-comments/batch")
    CommentResponses.CommentBatch generateBatch(@PathVariable String slug,
            @Valid @RequestBody CommentRequests.GenerateAiCommentBatchRequest request) {
        return mapper.toResponse(batches.generate(slug, mapper.toCommand(request)));
    }

    @PostMapping("/comments/{id}/ai-replies")
    CommentDetail generateReply(@PathVariable String id, @Valid @RequestBody GenerateAiCommentRequest request) {
        return mapper.toResponse(comments.generateAiReply(id, request.agentId()));
    }

    @GetMapping("/posts/{slug}/ai-summary")
    PostAiSummaryDetail summary(@PathVariable String slug) {
        return mapper.toResponse(comments.getAiSummary(slug));
    }

    @PostMapping("/posts/{slug}/ai-summary")
    PostAiSummaryDetail generateSummary(@PathVariable String slug) {
        return mapper.toResponse(comments.generateAiSummary(slug));
    }

    @GetMapping("/ai-summaries")
    AiSummaryCoverage summaryCoverage() {
        return mapper.toResponse(comments.getAiSummaryCoverage());
    }

    @PostMapping("/ai-summaries/backfill-next")
    AiSummaryCoverage backfillNextSummary() {
        return mapper.toResponse(comments.backfillNextAiSummary());
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
