package com.speaive.blog.application.result.creative;

import java.util.List;

public record WorkCollectionListResult(List<WorkCollectionResult> items) {
    public WorkCollectionListResult {
        items = List.copyOf(items);
    }
}
