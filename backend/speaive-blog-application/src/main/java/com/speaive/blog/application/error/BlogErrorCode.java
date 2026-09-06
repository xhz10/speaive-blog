package com.speaive.blog.application.error;

/** 应用用例对外报告的错误分类；HTTP 层将其转换为状态码和统一错误体。 */
public enum BlogErrorCode {
    /** 请求不符合用例或领域规则，通常映射为 HTTP 400。 */
    INVALID_REQUEST,
    /** 已登录但没有执行此操作的业务权限，映射为 HTTP 403。 */
    FORBIDDEN,
    /** 上传或导入文件名不合法，映射为 HTTP 400。 */
    INVALID_FILE_NAME,
    /** Markdown 内容或元数据不能解析，映射为 HTTP 400。 */
    INVALID_MARKDOWN,
    /** 图片格式或内容校验失败，映射为 HTTP 400。 */
    INVALID_IMAGE,
    /** 请求路径越出允许的数据目录，映射为 HTTP 400。 */
    PATH_OUTSIDE_DATA_DIR,
    /** 内容或文件超过大小限制，映射为 HTTP 413。 */
    TOO_LARGE,
    /** 目标不存在或对当前读取范围不可见，映射为 HTTP 404。 */
    NOT_FOUND,
    /** 内容 slug 已被活动记录占用，映射为 HTTP 409。 */
    SLUG_CONFLICT,
    /** 版本令牌过期或 CAS 未命中，映射为 HTTP 409。 */
    VERSION_CONFLICT,
    /** 相同文章版本、角色与回复目标的生成占位冲突，映射为 HTTP 409。 */
    GENERATION_CONFLICT,
    /** AI 服务未启用或未配置，映射为 HTTP 503。 */
    AI_UNAVAILABLE,
    /** 模型调用失败，映射为 HTTP 502。 */
    AI_GENERATION_FAILED,
    /** 数据库或媒体存储无法完成操作，映射为 HTTP 500。 */
    STORAGE_ERROR
}
