package com.speaive.blog.domain.post;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.util.Objects;

/**
 * 文章的一次合法状态变化，携带变更前后聚合与操作原因。它为仓储提供旧版本 CAS 条件和新修订快照，不是数据库 PO，也不是异步消息。
 *
 * @param previous 执行本次操作前的聚合状态，提供并发比较依据
 * @param current 通过领域校验后的新聚合状态
 * @param eventType 产生该修订的业务操作原因
 */
public record PostChange(Post previous, Post current, PostRevisionEventType eventType) {
    public PostChange {
        previous = Objects.requireNonNull(previous, "previous");
        current = Objects.requireNonNull(current, "current");
        eventType = Objects.requireNonNull(eventType, "eventType");

        if (eventType == PostRevisionEventType.CREATE || eventType == PostRevisionEventType.IMPORT) {
            throw invalid("创建事件不能表示已有文章的状态变更");
        }
        if (previous.archived() && eventType != PostRevisionEventType.RESTORE) {
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
            case RESTORE -> {
                if (previous.archived()) {
                    if (current.archived() || current.status() != PostStatus.DRAFT
                            || current.visibility() != PostVisibility.ADMIN_ONLY
                            || !current.content().equals(previous.content())) {
                        throw invalid("找回归档必须保留内容并恢复为私密草稿");
                    }
                } else if (current.archived() || current.status() != previous.status()) {
                    throw invalid("恢复历史内容不能改变文章状态");
                }
            }
            case UPDATE -> {
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
