package com.speaive.blog.interfaces.http;

import com.speaive.blog.application.port.in.BlogUseCase;
import com.speaive.blog.application.result.MediaContentResult;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class MediaController {
    private final BlogUseCase blog;

    public MediaController(BlogUseCase blog) {
        this.blog = blog;
    }

    @GetMapping("/media/{*path}")
    ResponseEntity<byte[]> read(@PathVariable String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        MediaContentResult media = blog.readMedia(normalizedPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, media.mimeType())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(media.bytes());
    }
}
