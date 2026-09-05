package com.speaive.blog.application.error;

/**
 * 应用用例错误，携带对外稳定的错误分类与可读说明。HTTP 适配器负责把它转换成状态码及 code/message 错误体。
 */
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
