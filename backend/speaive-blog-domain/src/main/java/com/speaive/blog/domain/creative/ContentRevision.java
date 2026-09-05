package com.speaive.blog.domain.creative;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 文章与小说历史版本的统一读取投影。保留来源类型和历史快照，用于时间机展示与恢复，不等于可直接写入的当前聚合。
 *
 * @param contentType 引用内容的业务类型：文章或小说片段
 * @param revision 从 1 开始的修订号，每次合法写操作推进一次
 * @param eventType 产生该修订的业务操作原因
 * @param slug 内容 URL 中的路径标识，不是不可重用的数据库 ID
 * @param title 标题
 * @param summary 历史内容的文章简介或小说节选
 * @param body 正文内容；格式与长度由当前业务类型约束
 * @param publishedAt 内容展示的发布时间；不能仅据此判断是否公开
 * @param tags 主题标签列表
 * @param cover 封面地址，未配置时可为空
 * @param status 当前业务状态，详见该字段的枚举类型
 * @param visibility 可见范围；独立于发布状态
 * @param updatedAt 最近一次修改或状态变化时间
 * @param recordedAt 历史快照写入时间
 */
public record ContentRevision(
        String contentId,
        CreativeContentType contentType,
        long revision,
        String eventType,
        String slug,
        String title,
        String summary,
        String body,
        Instant publishedAt,
        List<String> tags,
        String cover,
        String status,
        String visibility,
        Instant updatedAt,
        Instant recordedAt
) {
    public ContentRevision {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(body, "body");
        tags = tags == null ? List.of() : List.copyOf(tags);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (revision < 1) throw new IllegalArgumentException("revision 必须大于 0");
    }
}
