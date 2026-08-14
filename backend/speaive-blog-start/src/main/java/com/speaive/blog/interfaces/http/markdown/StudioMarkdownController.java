package com.speaive.blog.interfaces.http.markdown;

import com.speaive.blog.application.port.in.markdown.MarkdownUseCase;
import com.speaive.blog.interfaces.http.error.ApiErrorCode;
import com.speaive.blog.interfaces.http.error.ApiHttpException;
import com.speaive.blog.interfaces.http.post.PostHttpMapper;
import com.speaive.blog.interfaces.http.post.PostResponses.PostDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/studio")
public class StudioMarkdownController {
    private final MarkdownUseCase markdown;
    private final PostHttpMapper mapper;

    public StudioMarkdownController(MarkdownUseCase markdown, PostHttpMapper mapper) {
        this.markdown = markdown;
        this.mapper = mapper;
    }

    @PostMapping(path = "/import", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    PostDetail importMarkdown(@RequestPart("markdown") MultipartFile file) {
        return mapper.toResponse(markdown.importDraft(originalFileName(file), bytes(file)));
    }

    @PostMapping("/preview")
    PreviewResponse preview(@Valid @RequestBody PreviewRequest request) {
        return new PreviewResponse(markdown.preview(request.body()));
    }

    private static String originalFileName(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new ApiHttpException(ApiErrorCode.INVALID_REQUEST, "上传文件缺少文件名", HttpStatus.BAD_REQUEST);
        }
        return fileName;
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new ApiHttpException(ApiErrorCode.INVALID_REQUEST, "无法读取上传文件", HttpStatus.BAD_REQUEST);
        }
    }

    record PreviewRequest(@NotNull(message = "正文不能为空") String body) {
    }

    record PreviewResponse(String html) {
    }
}
