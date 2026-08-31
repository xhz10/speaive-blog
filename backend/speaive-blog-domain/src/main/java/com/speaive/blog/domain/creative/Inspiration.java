package com.speaive.blog.domain.creative;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

public record Inspiration(
        String id,
        String title,
        String body,
        InspirationKind kind,
        InspirationStatus status,
        boolean pinned,
        CreativeContentType targetType,
        String targetSlug,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
    public Inspiration {
        id = required(id, 36, "灵感 ID 不能为空");
        title = required(title, 120, "灵感标题不能为空");
        body = required(body, 10_000, "灵感内容不能为空");
        kind = Objects.requireNonNull(kind, "kind");
        status = Objects.requireNonNull(status, "status");
        targetSlug = optional(targetSlug, 100);
        if ((targetType == null) != (targetSlug == null)) {
            throw invalid("灵感的目标类型与目标内容必须同时设置");
        }
        if (revision < 1) throw invalid("灵感 revision 必须大于 0");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw invalid("灵感更新时间不能早于创建时间");
    }

    public static Inspiration create(
            String id, String title, String body, InspirationKind kind, boolean pinned, Instant now) {
        return new Inspiration(id, title, body, kind, InspirationStatus.INBOX, pinned,
                null, null, 1, now, now);
    }

    public Inspiration edit(String title, String body, InspirationKind kind, boolean pinned,
                            long expectedRevision, Instant now) {
        assertRevision(expectedRevision);
        return new Inspiration(id, title, body, kind, status, pinned, targetType, targetSlug,
                revision + 1, createdAt, nextTime(now));
    }

    public Inspiration transition(InspirationStatus nextStatus, CreativeContentType nextTargetType,
                                  String nextTargetSlug, long expectedRevision, Instant now) {
        assertRevision(expectedRevision);
        if (nextStatus == InspirationStatus.CONVERTED && (nextTargetType == null || nextTargetSlug == null)) {
            throw invalid("转化完成的灵感必须关联目标内容");
        }
        if (nextStatus != InspirationStatus.CONVERTED) {
            nextTargetType = null;
            nextTargetSlug = null;
        }
        return new Inspiration(id, title, body, kind, nextStatus, pinned, nextTargetType, nextTargetSlug,
                revision + 1, createdAt, nextTime(now));
    }

    public void assertRevision(long expectedRevision) {
        if (revision != expectedRevision) throw invalid("灵感已在其他页面更新，请刷新后重试");
    }

    private Instant nextTime(Instant value) {
        Instant time = Objects.requireNonNull(value, "now");
        if (time.isBefore(updatedAt)) throw invalid("灵感更新时间不能倒退");
        return time;
    }

    private static String required(String value, int max, String message) {
        String result = value == null ? "" : value.trim();
        int length = result.codePointCount(0, result.length());
        if (length == 0 || length > max || result.indexOf('\0') >= 0) throw invalid(message);
        return result;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return required(value, max, "关联内容 slug 不合法");
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_STATE, message);
    }
}
