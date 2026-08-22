package com.speaive.blog.interfaces.http;

import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.domain.PostVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

final class PostRequests {
    private PostRequests() {
    }

    record CreatePostRequest(
            @NotBlank(message = "slug 不能为空") @Size(max = 100, message = "slug 不能超过 100 个字符") String slug,
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题不能超过 200 个字符") String title,
            @Size(max = 500, message = "摘要不能超过 500 个字符") String description,
            Instant publishedAt,
            @Size(max = 20, message = "标签不能超过 20 个") List<@Size(min = 1, max = 40, message = "单个标签长度必须在 1 到 40 个字符之间") String> tags,
            String cover,
            @NotNull(message = "可见性不能为空") PostVisibility visibility,
            @NotNull(message = "正文不能为空") String body
    ) {
        PostWriteCommand toCommand() {
            return new PostWriteCommand(slug, title, description, publishedAt, tags, emptyToNull(cover), visibility,
                    body);
        }
    }

    record UpdatePostRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 200, message = "标题不能超过 200 个字符") String title,
            @Size(max = 500, message = "摘要不能超过 500 个字符") String description,
            Instant publishedAt,
            @Size(max = 20, message = "标签不能超过 20 个") List<@Size(min = 1, max = 40, message = "单个标签长度必须在 1 到 40 个字符之间") String> tags,
            String cover,
            @NotNull(message = "可见性不能为空") PostVisibility visibility,
            @NotNull(message = "正文不能为空") String body,
            @NotBlank(message = "version 不能为空") String version
    ) {
        PostWriteCommand toCommand(String slug) {
            return new PostWriteCommand(slug, title, description, publishedAt, tags, emptyToNull(cover), visibility,
                    body);
        }
    }

    record VersionRequest(@NotBlank(message = "version 不能为空") String version) {
    }

    record PreviewRequest(@NotNull(message = "正文不能为空") String body) {
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
