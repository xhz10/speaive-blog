package com.speaive.blog.domain;

public record MediaContent(String mimeType, byte[] bytes) {
    public MediaContent {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
