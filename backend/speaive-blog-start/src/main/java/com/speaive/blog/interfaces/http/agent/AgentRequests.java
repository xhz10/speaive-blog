package com.speaive.blog.interfaces.http.agent;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

final class AgentRequests {
    private AgentRequests() {
    }

    record CreateAgentRequest(
            @NotBlank(message = "Agent 用户名不能为空")
            @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,49}$", message = "Agent 用户名只能包含小写字母、数字、下划线和连字符")
            String username,
            @NotBlank(message = "Agent 显示名称不能为空")
            @Size(max = 100, message = "Agent 显示名称不能超过 100 个字符")
            String displayName,
            @Size(max = 2048, message = "头像地址不能超过 2048 个字符")
            String avatarUrl,
            @NotBlank(message = "系统提示词不能为空")
            @Size(max = 12000, message = "系统提示词不能超过 12000 个字符")
            String systemPrompt,
            @Size(max = 120, message = "模型名称不能超过 120 个字符")
            String model,
            @DecimalMin(value = "0", message = "temperature 不能小于 0")
            @DecimalMax(value = "2", message = "temperature 不能大于 2")
            double temperature,
            boolean canProcessPrivate,
            boolean enabled
    ) {
    }

    record UpdateAgentRequest(
            @NotBlank(message = "Agent 显示名称不能为空")
            @Size(max = 100, message = "Agent 显示名称不能超过 100 个字符")
            String displayName,
            @Size(max = 2048, message = "头像地址不能超过 2048 个字符")
            String avatarUrl,
            @NotBlank(message = "系统提示词不能为空")
            @Size(max = 12000, message = "系统提示词不能超过 12000 个字符")
            String systemPrompt,
            @Size(max = 120, message = "模型名称不能超过 120 个字符")
            String model,
            @DecimalMin(value = "0", message = "temperature 不能小于 0")
            @DecimalMax(value = "2", message = "temperature 不能大于 2")
            double temperature,
            boolean canProcessPrivate,
            boolean enabled,
            @Min(value = 1, message = "Agent 版本必须大于 0") long version
    ) {
    }
}
