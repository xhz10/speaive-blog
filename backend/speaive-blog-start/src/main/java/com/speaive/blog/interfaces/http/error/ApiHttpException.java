package com.speaive.blog.interfaces.http.error;

import org.springframework.http.HttpStatus;

/**
 * HTTP 入站层专用异常，携带认证、授权或请求处理所需的状态码与错误分类。
 */
public final class ApiHttpException extends RuntimeException {
    private final ApiErrorCode code;
    private final HttpStatus status;

    public ApiHttpException(ApiErrorCode code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ApiErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
