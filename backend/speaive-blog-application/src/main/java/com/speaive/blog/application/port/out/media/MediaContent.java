package com.speaive.blog.application.port.out.media;

import java.util.Arrays;
import java.util.Objects;

/**
 * 从存储适配器读出的媒体字节与内容类型，供应用层转换为对外读取结果。
 */
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
