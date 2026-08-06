package com.speaive.blog.application.result;

public record StoredMediaResult(String url, String relativePath, String mimeType, long size) {
}
