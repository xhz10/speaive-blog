package com.speaive.blog.interfaces.http.writing;

import jakarta.validation.constraints.*;
import java.util.List;
import java.time.Instant;

/** 会员写作入站请求；用户名和文章所有者只能从登录身份取得。 */
final class WritingRequests {
    private WritingRequests() { }
    record Permissions(@NotBlank @Pattern(regexp = "READER|WRITER") String role, boolean canPublish,
            boolean encryptionAllowed, @Positive long version) { }
    record Encryption(boolean encrypted, @Positive long version) { }
    record Write(@NotBlank @Size(max = 200) String title, @Size(max = 500) String description,
            Instant publishedAt, @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
            @Size(max = 2000) String cover, @Pattern(regexp = "PUBLIC|ADMIN_ONLY") String visibility,
            @NotNull @Size(max = 1000000) String body) { }
    record Update(@NotBlank @Size(max = 200) String title, @Size(max = 500) String description,
            Instant publishedAt, @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
            @Size(max = 2000) String cover, @Pattern(regexp = "PUBLIC|ADMIN_ONLY") String visibility,
            @NotNull @Size(max = 1000000) String body, @NotBlank @Size(max = 150) String version) { }
    record Version(@NotBlank @Size(max = 150) String version) { }
    record Restore(@Positive long revision, @NotBlank @Size(max = 150) String version) { }
}
