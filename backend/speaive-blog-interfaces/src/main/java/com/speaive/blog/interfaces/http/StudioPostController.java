package com.speaive.blog.interfaces.http;

import com.speaive.blog.application.BlogApplicationService;
import com.speaive.blog.domain.StoredMedia;
import com.speaive.blog.interfaces.http.PostRequests.CreatePostRequest;
import com.speaive.blog.interfaces.http.PostRequests.PreviewRequest;
import com.speaive.blog.interfaces.http.PostRequests.UpdatePostRequest;
import com.speaive.blog.interfaces.http.PostRequests.VersionRequest;
import com.speaive.blog.interfaces.http.PostResponses.PostDetail;
import com.speaive.blog.interfaces.http.PostResponses.PostList;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/studio")
public class StudioPostController {
    private final BlogApplicationService blog;

    public StudioPostController(BlogApplicationService blog) {
        this.blog = blog;
    }

    @GetMapping("/posts")
    PostList list() {
        return PostResponses.list(blog.listStudioPosts());
    }

    @GetMapping("/posts/{slug}")
    PostDetail get(@PathVariable String slug) {
        return PostResponses.detail(blog.getStudioPost(slug));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    PostDetail create(@Valid @RequestBody CreatePostRequest request) {
        return PostResponses.detail(blog.createDraft(request.toCommand()));
    }

    @PutMapping("/posts/{slug}")
    PostDetail update(@PathVariable String slug, @Valid @RequestBody UpdatePostRequest request) {
        return PostResponses.detail(blog.update(slug, request.version(), request.toCommand(slug)));
    }

    @PostMapping("/posts/{slug}/publish")
    PostDetail publish(@PathVariable String slug, @Valid @RequestBody VersionRequest request) {
        return PostResponses.detail(blog.publish(slug, request.version()));
    }

    @PostMapping("/posts/{slug}/unpublish")
    PostDetail unpublish(@PathVariable String slug, @Valid @RequestBody VersionRequest request) {
        return PostResponses.detail(blog.unpublish(slug, request.version()));
    }

    @PostMapping("/posts/{slug}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String slug, @Valid @RequestBody VersionRequest request) {
        blog.archive(slug, request.version());
    }

    @PostMapping(path = "/import", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    PostDetail importMarkdown(@RequestPart("markdown") MultipartFile markdown) {
        return PostResponses.detail(blog.importDraft(originalFileName(markdown), bytes(markdown)));
    }

    @PostMapping(path = "/media", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    StoredMedia uploadMedia(@RequestPart("image") MultipartFile image) {
        return blog.storeMedia(originalFileName(image), image.getContentType(), bytes(image));
    }

    @PostMapping("/preview")
    PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        return new PreviewResponse(blog.preview(request.body()));
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiHttpException("INVALID_REQUEST", "无法读取上传文件", HttpStatus.BAD_REQUEST);
        }
    }

    private static String originalFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new ApiHttpException("INVALID_REQUEST", "上传文件缺少文件名", HttpStatus.BAD_REQUEST);
        }
        return fileName;
    }

    record PreviewResponse(String html) {
    }
}
