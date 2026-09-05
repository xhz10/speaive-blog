package com.speaive.blog.domain.creative;

/** 跨类型创作工具中的内容类型，用于历史版本、作品集和分享定位。 */
public enum CreativeContentType {
    /** 普通博客文章，公开阅读路径为 /blog/{slug}/。 */
    POST,
    /** 小说片段，公开阅读路径为 /novels/{slug}/。 */
    NOVEL
}
