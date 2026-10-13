package com.speaive.blog.infrastructure.knowledge.persistence.po;

import java.time.Instant;
import java.util.List;

/** PostgreSQL material row; domain validation is performed when the adapter maps it back. */
public record HumorMaterialPo(
        String id, String excerpt, String sourceUrl, String sourceTitle, String sourceKind,
        List<String> topics, String mechanism, String analysis, String adaptation,
        int quality, boolean enabled, long version, Instant createdAt, Instant updatedAt) {
}
