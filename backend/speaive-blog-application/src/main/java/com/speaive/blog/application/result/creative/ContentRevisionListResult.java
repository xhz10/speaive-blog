package com.speaive.blog.application.result.creative;

import java.util.List;

public record ContentRevisionListResult(List<ContentRevisionResult> items) {
    public ContentRevisionListResult {
        items = List.copyOf(items);
    }
}
