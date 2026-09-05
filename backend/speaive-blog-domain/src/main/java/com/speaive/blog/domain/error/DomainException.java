package com.speaive.blog.domain.error;

import java.util.Objects;

/**
 * 领域规则被违反时抛出的异常，携带领域错误分类；由应用层转换为用例错误，领域层不决定 HTTP 状态码。
 */
public final class DomainException extends RuntimeException {
    private final DomainErrorCode code;

    public DomainException(DomainErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public DomainErrorCode code() {
        return code;
    }
}
