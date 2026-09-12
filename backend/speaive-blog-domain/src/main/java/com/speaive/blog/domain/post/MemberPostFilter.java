package com.speaive.blog.domain.post;

/** 作者文章列表的业务范围；仅筛选访问元数据，不给加密正文建立明文索引。 */
public enum MemberPostFilter {
    /** 尚未归档的全部文章。 */
    ALL,
    /** 草稿或仅本人可见的文章，不含归档。 */
    PRIVATE,
    /** 已发布且允许公开阅读的文章。 */
    PUBLIC,
    /** 已从活动列表移走、可以找回的归档文章。 */
    ARCHIVED
}
