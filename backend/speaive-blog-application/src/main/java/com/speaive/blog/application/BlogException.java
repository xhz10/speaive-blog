package com.speaive.blog.application;

public final class BlogException extends RuntimeException {
    private final BlogErrorCode code;

    public BlogException(BlogErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public BlogException(BlogErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public BlogErrorCode code() {
        return code;
    }
}
