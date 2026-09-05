package com.speaive.blog.application.port.in.media;

import com.speaive.blog.application.result.media.MediaContentResult;
import com.speaive.blog.application.result.media.StoredMediaResult;

/**
 * 媒体上传与读取的入站契约，以字节内容和应用结果模型隔离 HTTP MultipartFile。
 */
public interface MediaUseCase {
    StoredMediaResult storeMedia(String fileName, String mimeType, byte[] bytes);

    MediaContentResult readPublicMedia(String path);

    MediaContentResult readStudioMedia(String path);
}
