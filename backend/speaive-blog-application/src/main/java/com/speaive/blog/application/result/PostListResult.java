package com.speaive.blog.application.result;

import java.util.List;

public record PostListResult(List<PostSummaryResult> items, List<ContentScanErrorResult> errors) {
    public PostListResult {
        items = List.copyOf(items);
        errors = List.copyOf(errors);
    }
}
