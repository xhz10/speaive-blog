package com.speaive.blog.domain.agent;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.PostVisibility;

import java.time.Instant;
import java.util.Objects;

public final class AgentProfile {
    private static final int MAX_PROMPT_LENGTH = 12_000;
    private static final int MAX_MODEL_LENGTH = 120;

    private final Author identity;
    private final String systemPrompt;
    private final String model;
    private final double temperature;
    private final boolean canProcessPrivate;
    private final long promptVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AgentProfile(
            Author identity,
            String systemPrompt,
            String model,
            double temperature,
            boolean canProcessPrivate,
            long promptVersion,
            Instant createdAt,
            Instant updatedAt) {
        this.identity = requireAgentIdentity(identity);
        this.systemPrompt = requireText(systemPrompt, "Agent 系统提示词不能为空", MAX_PROMPT_LENGTH);
        this.model = optionalText(model, "模型名称过长", MAX_MODEL_LENGTH);
        this.temperature = requireTemperature(temperature);
        this.canProcessPrivate = canProcessPrivate;
        if (promptVersion < 1) {
            throw invalid("Agent 提示词版本必须大于 0");
        }
        this.promptVersion = promptVersion;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw invalid("Agent 更新时间不能早于创建时间");
        }
    }

    public static AgentProfile create(
            String id,
            String username,
            String displayName,
            String avatarUrl,
            String systemPrompt,
            String model,
            double temperature,
            boolean canProcessPrivate,
            boolean enabled,
            Instant now) {
        Author identity = new Author(id, username, displayName, AuthorType.AGENT, avatarUrl,
                enabled ? AuthorStatus.ACTIVE : AuthorStatus.DISABLED);
        return new AgentProfile(identity, systemPrompt, model, temperature, canProcessPrivate, 1, now, now);
    }

    public static AgentProfile rehydrate(
            Author identity,
            String systemPrompt,
            String model,
            double temperature,
            boolean canProcessPrivate,
            long promptVersion,
            Instant createdAt,
            Instant updatedAt) {
        return new AgentProfile(identity, systemPrompt, model, temperature, canProcessPrivate,
                promptVersion, createdAt, updatedAt);
    }

    public AgentProfile update(
            String displayName,
            String avatarUrl,
            String systemPrompt,
            String model,
            double temperature,
            boolean canProcessPrivate,
            boolean enabled,
            Instant now) {
        Instant updateTime = Objects.requireNonNull(now, "now");
        Author nextIdentity = new Author(
                id(), identity.username(), displayName, AuthorType.AGENT, avatarUrl,
                enabled ? AuthorStatus.ACTIVE : AuthorStatus.DISABLED);
        return new AgentProfile(nextIdentity, systemPrompt, model, temperature, canProcessPrivate,
                promptVersion + 1, createdAt, updateTime);
    }

    public void ensureCanGenerate(PostVisibility visibility) {
        if (!enabled()) {
            throw invalid("Agent 已停用，不能生成评论");
        }
        if (visibility == PostVisibility.ADMIN_ONLY && !canProcessPrivate) {
            throw invalid("该 Agent 未被允许读取仅自己可见的文章");
        }
    }

    public String id() {
        return identity.id();
    }

    public Author identity() {
        return identity;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String model() {
        return model;
    }

    public double temperature() {
        return temperature;
    }

    public boolean canProcessPrivate() {
        return canProcessPrivate;
    }

    public boolean enabled() {
        return identity.status() == AuthorStatus.ACTIVE;
    }

    public long promptVersion() {
        return promptVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String getId() {
        return id();
    }

    public Author getIdentity() {
        return identity();
    }

    public String getSystemPrompt() {
        return systemPrompt();
    }

    public String getModel() {
        return model();
    }

    public double getTemperature() {
        return temperature();
    }

    public boolean isCanProcessPrivate() {
        return canProcessPrivate();
    }

    public long getPromptVersion() {
        return promptVersion();
    }

    public Instant getCreatedAt() {
        return createdAt();
    }

    public Instant getUpdatedAt() {
        return updatedAt();
    }

    private static Author requireAgentIdentity(Author author) {
        if (author == null || author.type() != AuthorType.AGENT) {
            throw invalid("Agent 必须关联 AGENT 类型的作者身份");
        }
        return author;
    }

    private static double requireTemperature(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 2) {
            throw invalid("Agent temperature 必须在 0 到 2 之间");
        }
        return value;
    }

    private static String requireText(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw invalid(message);
        }
        return normalized;
    }

    private static String optionalText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw invalid(message);
        }
        return normalized;
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_AGENT, message);
    }
}
