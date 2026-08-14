package com.speaive.blog.domain.error;

import java.util.Objects;

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
