package com.speaive.blog.domain.novel;

import com.speaive.blog.domain.author.Author;

import java.time.Instant;

/**
 * 小说片段某一修订的不可变状态，用于聚合重建和版本令牌生成。
 *
 * @param id 当前对象的稳定标识，不应由展示名称替代
 * @param slug 内容 URL 中的路径标识，不是不可重用的数据库 ID
 * @param content 经过校验的内容值对象
 * @param author 内容署名身份的不可变表示
 * @param status 当前业务状态，详见该字段的枚举类型
 * @param visibility 可见范围；独立于发布状态
 * @param publishedAt 内容展示的发布时间；不能仅据此判断是否公开
 * @param createdAt 首次创建时间
 * @param updatedAt 最近一次修改或状态变化时间
 * @param revision 从 1 开始的修订号，每次合法写操作推进一次
 */
public record NovelFragmentSnapshot(
        String id,
        NovelFragmentSlug slug,
        NovelFragmentContent content,
        Author author,
        NovelFragmentStatus status,
        NovelFragmentVisibility visibility,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long revision
) {
    public String version() {
        return id + ":" + revision;
    }
}
