package com.speaive.blog.application.result.comment;

import java.util.List;

public record CommentListResult(List<CommentResult> items) {
    public CommentListResult {
        items = List.copyOf(items);
    }
}
