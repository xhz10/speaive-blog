package com.speaive.blog.interfaces.http;

import com.speaive.blog.application.BlogApplicationService;
import com.speaive.blog.domain.MediaContent;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class MediaController {
    private final BlogApplicationService blog;

    public MediaController(BlogApplicationService blog) {
        this.blog = blog;
    }

    @GetMapping("/media/{*path}")
    ResponseEntity<byte[]> read(@PathVariable String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        MediaContent media = blog.readMedia(normalizedPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, media.mimeType())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(media.bytes());
    }
}
