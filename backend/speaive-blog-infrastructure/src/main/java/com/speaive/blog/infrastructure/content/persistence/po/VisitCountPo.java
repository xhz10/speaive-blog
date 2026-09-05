package com.speaive.blog.infrastructure.content.persistence.po;

/** SQL 分组计数投影，不包含领域决策。 */
public record VisitCountPo(String key, String label, long count) {}
