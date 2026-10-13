package com.speaive.blog.application.result.knowledge;

import java.time.Instant;
import java.util.List;

public record HumorMaterialResult(String id, String excerpt, String sourceUrl, String sourceTitle,
        String sourceKind, List<String> topics, String mechanism, String analysis, String adaptation,
        int quality, boolean enabled, long version, Instant createdAt, Instant updatedAt, double score) {}
