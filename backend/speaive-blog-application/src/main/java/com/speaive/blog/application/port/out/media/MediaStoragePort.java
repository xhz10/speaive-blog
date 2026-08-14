package com.speaive.blog.application.port.out.media;

public interface MediaStoragePort {
    StoredMedia store(String fileName, String declaredMimeType, byte[] bytes);

    MediaContent read(String relativePath);
}
