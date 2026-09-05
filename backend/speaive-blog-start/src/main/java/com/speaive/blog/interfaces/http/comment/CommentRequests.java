package com.speaive.blog.interfaces.http.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
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
    /** 批量角色选择；大小限制也在应用层检查，避免其他入口绕过。 */
    record GenerateAiCommentBatchRequest(
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 36) String> agentIds) {}
}
