package com.speaive.blog.domain.creative;

/** 作品集的可见范围；公开作品集也只展示其中当前已公开的内容。 */
public enum CreativeVisibility {
    /** 公开作品集；私密或未发布的条目会被过滤，没有可见条目的作品集不会公开展示。 */
    PUBLIC,
    /** 私密作品集，仅在站长工作区读取。 */
    ADMIN_ONLY
}
