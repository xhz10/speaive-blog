package com.speaive.blog.application.result.creative;

import java.util.List;

public record EditorialReviewListResult(List<EditorialReviewResult> items) {
    public EditorialReviewListResult {
        items = List.copyOf(items);
    }
}
