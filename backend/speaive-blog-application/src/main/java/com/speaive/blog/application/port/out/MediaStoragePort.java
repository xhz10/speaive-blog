package com.speaive.blog.application.port.out;

import com.speaive.blog.domain.MediaContent;
import com.speaive.blog.domain.StoredMedia;

public interface MediaStoragePort {
    StoredMedia store(String fileName, String declaredMimeType, byte[] bytes);

    MediaContent read(String relativePath);
}
