package com.speaive.blog.application.port.out.media;

/** 媒体读取范围，由用例传递给存储适配器执行引用可见性检查。 */
public enum MediaReadScope {
    /** 访客读取，只允许被当前公开文章引用的受管理媒体。 */
    PUBLIC,
    /** 管理员读取，允许访问工作区中的受管理媒体。 */
    STUDIO
}
