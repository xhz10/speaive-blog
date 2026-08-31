package com.speaive.blog.application.result.creative;

import java.util.List;

public record InspirationListResult(List<InspirationResult> items) {
    public InspirationListResult {
        items = List.copyOf(items);
    }
}
