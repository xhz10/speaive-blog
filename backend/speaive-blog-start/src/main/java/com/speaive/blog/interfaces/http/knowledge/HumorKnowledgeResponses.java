package com.speaive.blog.interfaces.http.knowledge;

import java.time.Instant;
import java.util.List;

public final class HumorKnowledgeResponses {
    private HumorKnowledgeResponses() {}
    public record MaterialDetail(String id, String excerpt, String sourceUrl, String sourceTitle,
            String sourceKind, List<String> topics, String mechanism, String analysis, String adaptation,
            int quality, boolean enabled, long version, Instant createdAt, Instant updatedAt, double score) {}
    public record MaterialList(List<MaterialDetail> items) {}
}
