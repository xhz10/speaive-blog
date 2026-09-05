package com.speaive.blog.domain.author;

/** 内容身份是否启用；禁用不会删除历史署名。 */
public enum AuthorStatus {
    /** 启用身份；是否可以写作还需结合 AuthorType 判断。 */
    ACTIVE,
    /** 停用身份；不再允许以该身份创建新内容。 */
    DISABLED
}
