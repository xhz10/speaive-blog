package com.speaive.blog.application.result.media;

public record StoredMediaResult(String url, String relativePath, String mimeType, long size) {
}
