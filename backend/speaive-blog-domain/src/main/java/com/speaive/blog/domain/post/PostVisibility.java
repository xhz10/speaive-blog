package com.speaive.blog.domain.post;

/** 文章的可见范围；控制谁可以读取，不代替发布状态。 */
public enum PostVisibility {
    /** 公开范围：文章满足对应发布规则后可供访客读取。 */
    PUBLIC,
    /** 仅管理员：文章及受保护的关联内容只供站长查看，会员身份不等于管理员。 */
    ADMIN_ONLY
}
