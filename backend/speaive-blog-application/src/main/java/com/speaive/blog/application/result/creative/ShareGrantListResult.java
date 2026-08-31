package com.speaive.blog.application.result.creative;

import java.util.List;

public record ShareGrantListResult(List<ShareGrantResult> items) {
    public ShareGrantListResult {
        items = List.copyOf(items);
    }
}
