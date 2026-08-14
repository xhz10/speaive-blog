package com.speaive.blog.interfaces.http.media;

final class MediaResponses {
    private MediaResponses() {
    }

    record StoredMediaResponse(String url, String relativePath, String mimeType, long size) {
    }
}
