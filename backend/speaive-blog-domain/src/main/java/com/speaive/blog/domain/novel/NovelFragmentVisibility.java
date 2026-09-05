package com.speaive.blog.domain.novel;

/** 小说片段的可见范围；控制谁可以读取，不代替发布状态。 */
public enum NovelFragmentVisibility {
    /** 公开范围：小说片段满足对应发布规则后可供访客读取。 */
    PUBLIC,
    /** 仅管理员：小说片段及受保护的关联内容只供站长查看，会员身份不等于管理员。 */
    ADMIN_ONLY
}
