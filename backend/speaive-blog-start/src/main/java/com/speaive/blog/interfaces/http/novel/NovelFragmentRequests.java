package com.speaive.blog.interfaces.http.novel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class NovelFragmentRequests {
    private NovelFragmentRequests() {
    }

    record CreateNovelFragmentRequest(
            @NotBlank(message = "slug 不能为空")
            @Size(max = 100, message = "slug 不能超过 100 个字符") String slug,
            @NotBlank(message = "片段标题不能为空")
            @Size(max = 200, message = "片段标题不能超过 200 个字符") String title,
            @Size(max = 500, message = "片段简介不能超过 500 个字符") String excerpt,
            @Pattern(regexp = "PUBLIC|ADMIN_ONLY", message = "visibility 只能是 PUBLIC 或 ADMIN_ONLY")
            String visibility,
            @NotNull(message = "片段正文不能为空") String body
    ) {
    }

    record UpdateNovelFragmentRequest(
            @NotBlank(message = "片段标题不能为空")
            @Size(max = 200, message = "片段标题不能超过 200 个字符") String title,
            @Size(max = 500, message = "片段简介不能超过 500 个字符") String excerpt,
            @NotBlank(message = "visibility 不能为空")
            @Pattern(regexp = "PUBLIC|ADMIN_ONLY", message = "visibility 只能是 PUBLIC 或 ADMIN_ONLY")
            String visibility,
            @NotNull(message = "片段正文不能为空") String body,
            @NotBlank(message = "version 不能为空") String version
    ) {
    }

    record VersionRequest(@NotBlank(message = "version 不能为空") String version) {
    }
}
