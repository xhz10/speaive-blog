package com.speaive.blog.interfaces.http;

import org.springframework.http.HttpStatus;

public final class ApiHttpException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ApiHttpException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
