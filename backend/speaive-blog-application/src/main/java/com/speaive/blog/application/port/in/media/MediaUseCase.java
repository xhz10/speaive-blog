package com.speaive.blog.application.port.in.media;

import com.speaive.blog.application.result.media.MediaContentResult;
import com.speaive.blog.application.result.media.StoredMediaResult;

public interface MediaUseCase {
    StoredMediaResult storeMedia(String fileName, String mimeType, byte[] bytes);

    MediaContentResult readMedia(String path);
}
