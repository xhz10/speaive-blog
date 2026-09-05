package com.speaive.blog.application.port.out.persistence;

/** 评论仓储的读取范围；文章是否可读由外层用例先判断。 */
public enum CommentQueryScope {
    /** 审核工作区范围，包含待审核、已公开和已隐藏评论。 */
    STUDIO,
    /** 公开评论范围，只返回 PUBLISHED 评论，不替代文章权限检查。 */
    PUBLISHED
}
