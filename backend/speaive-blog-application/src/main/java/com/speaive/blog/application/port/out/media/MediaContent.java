package com.speaive.blog.application.port.out.media;

import java.util.Arrays;
import java.util.Objects;

public record MediaContent(String mimeType, byte[] bytes) {
    public MediaContent {
        mimeType = Objects.requireNonNull(mimeType, "mimeType");
        bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
