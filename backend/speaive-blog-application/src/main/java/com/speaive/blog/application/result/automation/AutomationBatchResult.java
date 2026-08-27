package com.speaive.blog.application.result.automation;

public record AutomationBatchResult(int processed, int succeeded, int skipped, int retried, int failed) {
}
