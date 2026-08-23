package com.speaive.blog.interfaces.http.media;

import com.speaive.blog.application.port.in.media.MediaUseCase;
import com.speaive.blog.interfaces.http.error.ApiErrorCode;
import com.speaive.blog.interfaces.http.error.ApiHttpException;
import com.speaive.blog.interfaces.http.media.MediaResponses.StoredMediaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/studio")
public class StudioMediaController {
    private final MediaUseCase media;
    private final MediaHttpMapper mapper;

    public StudioMediaController(MediaUseCase media, MediaHttpMapper mapper) {
        this.media = media;
        this.mapper = mapper;
    }

    @GetMapping("/media/{*path}")
    ResponseEntity<byte[]> read(@PathVariable String path) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        var content = media.readStudioMedia(normalizedPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.mimeType())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(content.bytes());
    }

    @PostMapping(path = "/media", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    StoredMediaResponse upload(@RequestPart("image") MultipartFile image) {
        return mapper.toResponse(media.storeMedia(originalFileName(image), image.getContentType(), bytes(image)));
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiHttpException(ApiErrorCode.INVALID_REQUEST, "无法读取上传文件", HttpStatus.BAD_REQUEST);
        }
    }

    private static String originalFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new ApiHttpException(ApiErrorCode.INVALID_REQUEST, "上传文件缺少文件名", HttpStatus.BAD_REQUEST);
        }
        return fileName;
    }
}
