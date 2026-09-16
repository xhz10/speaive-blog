package com.speaive.blog.interfaces.http.error;

import org.springframework.http.HttpStatus;

/**
 * HTTP 入站层专用异常，携带认证、授权或请求处理所需的状态码与错误分类。
 */
public final class ApiHttpException extends RuntimeException {
    private final ApiErrorCode code;
    private final HttpStatus status;
    private final Long retryAfterSeconds;

    public ApiHttpException(ApiErrorCode code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    /** 限流剩余等待秒数；通过 Retry-After 响应头传递，不改变错误体结构。 */
    public ApiHttpException(ApiErrorCode code, String message, HttpStatus status, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ApiErrorCode code() {
        return code;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public HttpStatus status() {
        return status;
    }
}
