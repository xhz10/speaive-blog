package com.speaive.blog.domain.novel;

/** 小说片段修订的操作原因；当前没有文章那样的归档操作。 */
public enum NovelFragmentRevisionEventType {
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
