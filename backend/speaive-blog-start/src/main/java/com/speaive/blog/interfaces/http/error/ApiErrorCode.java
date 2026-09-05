package com.speaive.blog.interfaces.http.error;

/** HTTP 入站层自身的错误分类，覆盖认证、授权、参数校验与限流；业务错误沿用 BlogErrorCode。 */
public enum ApiErrorCode {
    /** HTTP 参数、JSON 或表单校验失败。 */
    INVALID_REQUEST,
    /** 登录用户名或密码不正确。 */
    INVALID_CREDENTIALS,
    /** 当前请求缺少有效登录身份。 */
    UNAUTHORIZED,
    /** 已识别请求但不允许访问，或写请求未通过 CSRF 校验。 */
    FORBIDDEN,
    /** 登录尝试等请求触发限流，需要稍后再试。 */
    RATE_LIMITED,
    /** 上传请求超过 HTTP 接收大小上限。 */
    TOO_LARGE
}
