package com.speaive.blog.application.result;

import java.util.Arrays;
import java.util.Objects;

public record MediaContentResult(String mimeType, byte[] bytes) {
    public MediaContentResult {
        mimeType = Objects.requireNonNull(mimeType, "mimeType");
        bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
