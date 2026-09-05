package com.speaive.blog.application.port.out.persistence;

/** 小说片段仓储的读取范围，由用例指定；它不是用户权限，调用前仍需完成身份校验。 */
public enum NovelFragmentQueryScope {
    /** 站长工作区范围，包含草稿与私密小说片段。 */
    STUDIO,
    /** 公共阅读范围，只返回已发布且可见性为 PUBLIC 的小说片段。 */
    PUBLISHED
}
