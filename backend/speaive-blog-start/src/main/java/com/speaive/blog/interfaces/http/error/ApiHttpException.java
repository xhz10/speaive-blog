package com.speaive.blog.interfaces.http.error;

import org.springframework.http.HttpStatus;

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
