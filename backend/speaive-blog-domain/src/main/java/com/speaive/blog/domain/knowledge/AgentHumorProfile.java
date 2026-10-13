package com.speaive.blog.domain.knowledge;

import java.util.Objects;

/** 按稳定角色 ID 绑定素材偏好，不依赖可修改的显示名称。 */
public record AgentHumorProfile(String agentId, HumorMechanism mechanism, boolean enabled) {
    public AgentHumorProfile {
        if (agentId == null || agentId.isBlank() || agentId.length() > 36) {
            throw new IllegalArgumentException("角色 ID 不合法");
        }
        Objects.requireNonNull(mechanism, "mechanism");
    }
}
