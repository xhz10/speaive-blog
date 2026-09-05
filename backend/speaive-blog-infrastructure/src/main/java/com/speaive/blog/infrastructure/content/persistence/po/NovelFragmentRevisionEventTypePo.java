package com.speaive.blog.infrastructure.content.persistence.po;

/** NovelFragmentRevisionEventType 的数据库存储枚举。通过持久化边界映射为领域类型；名称与现有表值及 Flyway 约束保持兼容。 */
public enum NovelFragmentRevisionEventTypePo {
    /** 通过写作台创建初始草稿快照。 */
    CREATE,
    /** 修改内容或可见范围，保留原有发布状态。 */
    UPDATE,
    /** 发布内容；可见范围仍由 visibility 决定。 */
    PUBLISH,
    /** 撤回为草稿，保留内容与历史。 */
    UNPUBLISH,
    /** 把历史内容恢复成一个新修订，不回退版本号，也不自动改变当前发布状态。 */
    RESTORE
}
