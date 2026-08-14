package com.speaive.blog.application.port.out.media;

public record StoredMedia(String url, String relativePath, String mimeType, long size) {
}
