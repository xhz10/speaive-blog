package com.speaive.blog.domain;

public record StoredMedia(String url, String relativePath, String mimeType, long size) {
}
