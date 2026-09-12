package com.speaive.blog.domain.post;

/** 文章修订的操作原因，用于历史快照审计；它不是文章状态，也不是消息总线上的领域事件。 */
public enum PostRevisionEventType {
    /** 通过写作台创建初始草稿快照。 */
    CREATE,
    /** 从 Markdown 导入文章并记录初始快照。 */
    IMPORT,
    /** 修改内容或可见范围，保留原有发布状态。 */
    UPDATE,
    /** 发布内容；可见范围仍由 visibility 决定。 */
    PUBLISH,
    /** 撤回为草稿，保留内容与历史。 */
    UNPUBLISH,
    /** 记录归档快照并删除活动文章；历史保留，原 slug 可以重用。 */
    ARCHIVE,
    /** 把历史内容恢复成新修订；活动文章保留发布状态，找回归档必须回到私密草稿，不回退版本号。 */
    RESTORE
}
