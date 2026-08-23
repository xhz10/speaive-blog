package com.speaive.blog.interfaces.http.media;

import com.speaive.blog.application.port.in.media.MediaUseCase;
import com.speaive.blog.application.result.media.MediaContentResult;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaController {
    private final MediaUseCase media;

    public MediaController(MediaUseCase media) {
        this.media = media;
    }

    @GetMapping("/media/{*path}")
    ResponseEntity<byte[]> read(@PathVariable String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        MediaContentResult content = media.readPublicMedia(normalizedPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.mimeType())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }
}
