package com.speaive.blog.application.service;

import com.speaive.blog.application.port.in.media.MediaUseCase;
import com.speaive.blog.application.port.out.media.MediaContent;
import com.speaive.blog.application.port.out.media.MediaStoragePort;
import com.speaive.blog.application.port.out.media.MediaReadScope;
import com.speaive.blog.application.port.out.media.StoredMedia;
import com.speaive.blog.application.result.media.MediaContentResult;
import com.speaive.blog.application.result.media.StoredMediaResult;

import java.util.Objects;

public final class MediaApplicationService implements MediaUseCase {
    private final MediaStoragePort media;

    public MediaApplicationService(MediaStoragePort media) {
        this.media = Objects.requireNonNull(media, "media");
    }

    @Override
    public StoredMediaResult storeMedia(String fileName, String mimeType, byte[] bytes) {
        StoredMedia stored = media.store(fileName, mimeType, bytes);
        return new StoredMediaResult(stored.url(), stored.relativePath(), stored.mimeType(), stored.size());
    }

    @Override
    public MediaContentResult readPublicMedia(String path) {
        return read(path, MediaReadScope.PUBLIC);
    }

    @Override
    public MediaContentResult readStudioMedia(String path) {
        return read(path, MediaReadScope.STUDIO);
    }

    private MediaContentResult read(String path, MediaReadScope scope) {
        MediaContent content = media.read(path, scope);
        return new MediaContentResult(content.mimeType(), content.bytes());
    }
}
