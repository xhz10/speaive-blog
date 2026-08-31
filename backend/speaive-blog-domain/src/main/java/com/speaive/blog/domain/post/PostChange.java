package com.speaive.blog.domain.post;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.util.Objects;

public record PostChange(Post previous, Post current, PostRevisionEventType eventType) {
    public PostChange {
        previous = Objects.requireNonNull(previous, "previous");
        current = Objects.requireNonNull(current, "current");
        eventType = Objects.requireNonNull(eventType, "eventType");

        if (eventType == PostRevisionEventType.CREATE || eventType == PostRevisionEventType.IMPORT) {
            throw invalid("创建事件不能表示已有文章的状态变更");
        }
        if (previous.archived()) {
            throw invalid("已归档文章不能产生新变更");
        }
        if (!previous.id().equals(current.id())
                || !previous.slugValue().equals(current.slugValue())
                || !previous.author().equals(current.author())
                || !previous.createdAt().equals(current.createdAt())) {
            throw invalid("文章变更不能修改 ID、slug、作者或创建时间");
        }
        if (current.revision() != previous.revision() + 1) {
            throw invalid("文章变更必须且只能递增一次 revision");
        }
        if (current.updatedAt().isBefore(previous.updatedAt())) {
            throw invalid("文章变更时间不能早于上一版本");
        }
        validateEventResult(previous, current, eventType);
    }

    public long expectedRevision() {
        return previous.revision();
    }

    public String expectedVersion() {
        return previous.version();
    }

    private static void validateEventResult(Post previous, Post current, PostRevisionEventType eventType) {
        switch (eventType) {
            case UPDATE, RESTORE -> {
                if (current.archived() || current.status() != previous.status()) {
                    throw invalid("内容修改事件不能改变文章状态");
                }
            }
            case PUBLISH -> {
                if (current.archived() || current.status() != PostStatus.PUBLISHED
                        || !current.content().equals(previous.content())) {
                    throw invalid("发布事件必须产生已发布文章");
                }
            }
            case UNPUBLISH -> {
                if (current.archived() || current.status() != PostStatus.DRAFT
                        || !current.content().equals(previous.content())) {
                    throw invalid("撤回事件必须产生草稿文章");
                }
            }
            case ARCHIVE -> {
                if (!current.archived() || current.status() != previous.status()
                        || !current.content().equals(previous.content())) {
                    throw invalid("归档事件必须产生归档快照");
                }
            }
            case CREATE, IMPORT -> throw invalid("创建事件不能表示已有文章的状态变更");
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_STATE, message);
    }
}
