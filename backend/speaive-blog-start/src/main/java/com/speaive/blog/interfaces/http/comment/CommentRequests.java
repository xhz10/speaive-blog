package com.speaive.blog.interfaces.http.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class CommentRequests {
    private CommentRequests() {
    }

    record GenerateAiCommentRequest(
            @NotBlank(message = "Agent ID 不能为空")
            @Size(max = 36, message = "Agent ID 不合法")
            String agentId
    ) {
    }
}
