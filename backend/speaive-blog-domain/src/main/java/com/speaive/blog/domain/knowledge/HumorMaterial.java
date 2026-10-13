package com.speaive.blog.domain.knowledge;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/** 精选素材聚合：短摘录、来源、笑点分析和原创迁移示例一起版本化维护。 */
public record HumorMaterial(
        String id, String excerpt, String sourceUrl, String sourceTitle, HumorSourceKind sourceKind,
        List<String> topics, HumorMechanism mechanism, String analysis, String adaptation,
        int quality, boolean enabled, long version, Instant createdAt, Instant updatedAt) {
    public HumorMaterial {
        id = text(id, "素材 ID", 80);
        excerpt = text(excerpt, "摘录", 500);
        sourceUrl = text(sourceUrl, "来源链接", 2048);
        sourceTitle = text(sourceTitle, "来源标题", 200);
        analysis = text(analysis, "笑点分析", 1200);
        adaptation = text(adaptation, "原创迁移示例", 1200);
        if (sourceKind == null || mechanism == null) throw invalid("来源类别与幽默机制不能为空");
        try {
            URI source = URI.create(sourceUrl);
            if (!("https".equalsIgnoreCase(source.getScheme()) || "http".equalsIgnoreCase(source.getScheme()))
                    || source.getHost() == null || source.getUserInfo() != null) {
                throw invalid("来源必须是有效的 http/https 链接");
            }
        } catch (IllegalArgumentException e) {
            throw invalid("来源链接格式无效");
        }
        if (topics == null || topics.isEmpty() || topics.size() > 12) throw invalid("主题词应为 1 到 12 个");
        topics = topics.stream().map(topic -> text(topic, "主题词", 50)).distinct().toList();
        if (quality < 1 || quality > 5) throw invalid("素材质量应为 1 到 5");
        if (version < 1) throw invalid("素材版本必须大于 0");
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            throw invalid("素材创建与更新时间无效");
        }
    }

    public static HumorMaterial create(String id, String excerpt, String sourceUrl, String sourceTitle,
            HumorSourceKind sourceKind, List<String> topics, HumorMechanism mechanism, String analysis,
            String adaptation, int quality, boolean enabled, Instant now) {
        return new HumorMaterial(id, excerpt, sourceUrl, sourceTitle, sourceKind, topics, mechanism,
                analysis, adaptation, quality, enabled, 1, now, now);
    }

    public HumorMaterial revise(String excerpt, String sourceUrl, String sourceTitle, HumorSourceKind sourceKind,
            List<String> topics, HumorMechanism mechanism, String analysis, String adaptation,
            int quality, boolean enabled, Instant now) {
        if (now == null || now.isBefore(updatedAt)) throw invalid("素材更新时间不能倒退");
        return new HumorMaterial(id, excerpt, sourceUrl, sourceTitle, sourceKind, topics, mechanism,
                analysis, adaptation, quality, enabled, version + 1, createdAt, now);
    }

    private static String text(String value, String label, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0
                || normalized.codePointCount(0, normalized.length()) > max) {
            throw invalid(label + "不能为空且不能超过 " + max + " 个字符");
        }
        return normalized;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_CONTENT, message);
    }
}
