package com.speaive.blog.domain.agent;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.PostVisibility;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class AgentProfile {
    private static final int MAX_PROMPT_LENGTH = 12_000;
    private static final int MAX_MODEL_LENGTH = 120;
    private static final int MAX_REVIEW_NOTE_LENGTH = 500;
    private static final int MAX_AUTO_TAGS = 20;

    private final Author identity;
    private final String ownerAccountId;
    private final String systemPrompt;
    private final String model;
    private final double temperature;
    private final boolean canProcessPrivate;
    private final boolean enabledRequested;
    private final AgentReviewStatus reviewStatus;
    private final String reviewNote;
    private final Instant reviewedAt;
    private final boolean autoCommentEnabled;
    private final boolean autoCommentAllPosts;
    private final List<String> autoCommentTags;
    private final long promptVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AgentProfile(
            Author identity,
            String ownerAccountId,
            String systemPrompt,
            String model,
            double temperature,
            boolean canProcessPrivate,
            boolean enabledRequested,
            AgentReviewStatus reviewStatus,
            String reviewNote,
            Instant reviewedAt,
            boolean autoCommentEnabled,
            boolean autoCommentAllPosts,
            List<String> autoCommentTags,
            long promptVersion,
            Instant createdAt,
            Instant updatedAt) {
        this.identity = requireAgentIdentity(identity);
        this.ownerAccountId = optionalId(ownerAccountId);
        this.systemPrompt = requireText(systemPrompt, "Agent 系统提示词不能为空", MAX_PROMPT_LENGTH);
        this.model = optionalText(model, "模型名称过长", MAX_MODEL_LENGTH);
        this.temperature = requireTemperature(temperature);
        this.canProcessPrivate = canProcessPrivate;
        this.enabledRequested = enabledRequested;
        this.reviewStatus = Objects.requireNonNull(reviewStatus, "reviewStatus");
        this.reviewNote = optionalText(reviewNote, "审核说明过长", MAX_REVIEW_NOTE_LENGTH);
        this.reviewedAt = reviewedAt;
        this.autoCommentEnabled = autoCommentEnabled;
        this.autoCommentAllPosts = autoCommentAllPosts;
        this.autoCommentTags = normalizeTags(autoCommentTags);
        if (promptVersion < 1) {
            throw invalid("Agent 提示词版本必须大于 0");
        }
        this.promptVersion = promptVersion;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw invalid("Agent 更新时间不能早于创建时间");
        }
        validateOwnershipAndReviewState();
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
        Author identity = identity(id, username, displayName, avatarUrl, enabled);
        return new AgentProfile(identity, null, systemPrompt, model, temperature, canProcessPrivate,
                enabled, AgentReviewStatus.APPROVED, null, now, false, false, List.of(), 1, now, now);
    }

    public static AgentProfile createOwned(
            String id,
            String ownerAccountId,
            String username,
            String displayName,
            String avatarUrl,
            String systemPrompt,
            double temperature,
            boolean autoCommentEnabled,
            boolean autoCommentAllPosts,
            List<String> autoCommentTags,
            Instant now) {
        return new AgentProfile(identity(id, username, displayName, avatarUrl, false), ownerAccountId,
                systemPrompt, null, temperature, false, true, AgentReviewStatus.PENDING, null, null,
                autoCommentEnabled, autoCommentAllPosts, autoCommentTags, 1, now, now);
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
        return new AgentProfile(identity, null, systemPrompt, model, temperature, canProcessPrivate,
                identity.status() == AuthorStatus.ACTIVE, AgentReviewStatus.APPROVED, null, createdAt,
                false, false, List.of(), promptVersion, createdAt, updatedAt);
    }

    public static AgentProfile rehydrate(
            Author identity,
            String ownerAccountId,
            String systemPrompt,
            String model,
            double temperature,
            boolean canProcessPrivate,
            boolean enabledRequested,
            AgentReviewStatus reviewStatus,
            String reviewNote,
            Instant reviewedAt,
            boolean autoCommentEnabled,
            boolean autoCommentAllPosts,
            List<String> autoCommentTags,
            long promptVersion,
            Instant createdAt,
            Instant updatedAt) {
        return new AgentProfile(identity, ownerAccountId, systemPrompt, model, temperature, canProcessPrivate,
                enabledRequested, reviewStatus, reviewNote, reviewedAt, autoCommentEnabled,
                autoCommentAllPosts, autoCommentTags, promptVersion, createdAt, updatedAt);
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
        if (communityOwned()) {
            throw invalid("会员 Agent 需要通过会员配置和审核流程修改");
        }
        return copy(identity(id(), identity.username(), displayName, avatarUrl, enabled), null,
                systemPrompt, model, temperature, canProcessPrivate, enabled,
                AgentReviewStatus.APPROVED, null, now, false, false, List.of(), now);
    }

    public AgentProfile updateOwnedProfile(
            String accountId,
            String displayName,
            String avatarUrl,
            String systemPrompt,
            double temperature,
            Instant now) {
        ensureOwnedBy(accountId);
        return copy(identity(id(), identity.username(), displayName, avatarUrl, false), ownerAccountId,
                systemPrompt, null, temperature, false, enabledRequested,
                AgentReviewStatus.PENDING, null, null, autoCommentEnabled,
                autoCommentAllPosts, autoCommentTags, now);
    }

    public AgentProfile configureAutomation(
            String accountId,
            boolean enabledRequested,
            boolean autoCommentEnabled,
            boolean autoCommentAllPosts,
            List<String> autoCommentTags,
            Instant now) {
        ensureOwnedBy(accountId);
        boolean effective = reviewStatus == AgentReviewStatus.APPROVED && enabledRequested;
        return copy(identity(id(), identity.username(), identity.displayName(), identity.avatarUrl(), effective),
                ownerAccountId, systemPrompt, null, temperature, false, enabledRequested, reviewStatus,
                reviewNote, reviewedAt, autoCommentEnabled, autoCommentAllPosts, autoCommentTags, now);
    }

    public AgentProfile approve(Instant now) {
        if (!communityOwned()) {
            throw invalid("站长 Agent 不需要会员审核");
        }
        return copy(identity(id(), identity.username(), identity.displayName(), identity.avatarUrl(), enabledRequested),
                ownerAccountId, systemPrompt, null, temperature, false, enabledRequested,
                AgentReviewStatus.APPROVED, null, now, autoCommentEnabled,
                autoCommentAllPosts, autoCommentTags, now);
    }

    public AgentProfile reject(String note, Instant now) {
        if (!communityOwned()) {
            throw invalid("站长 Agent 不需要会员审核");
        }
        String reason = requireText(note, "拒绝 Agent 时必须填写原因", MAX_REVIEW_NOTE_LENGTH);
        return copy(identity(id(), identity.username(), identity.displayName(), identity.avatarUrl(), false),
                ownerAccountId, systemPrompt, null, temperature, false, enabledRequested,
                AgentReviewStatus.REJECTED, reason, now, autoCommentEnabled,
                autoCommentAllPosts, autoCommentTags, now);
    }

    public void ensureCanGenerate(PostVisibility visibility) {
        if (reviewStatus != AgentReviewStatus.APPROVED || !enabled()) {
            throw invalid("Agent 尚未通过审核或已停用，不能生成评论");
        }
        if (visibility == PostVisibility.ADMIN_ONLY && !canProcessPrivate) {
            throw invalid("该 Agent 未被允许读取仅自己可见的文章");
        }
    }

    public boolean matchesAutomaticTags(List<String> postTags) {
        if (!communityOwned() || !enabled() || reviewStatus != AgentReviewStatus.APPROVED
                || !autoCommentEnabled) {
            return false;
        }
        if (autoCommentAllPosts) {
            return true;
        }
        Set<String> wanted = new LinkedHashSet<>(autoCommentTags);
        return postTags != null && postTags.stream()
                .map(AgentProfile::normalizeTag)
                .anyMatch(wanted::contains);
    }

    public void ensureOwnedBy(String accountId) {
        if (!communityOwned() || !ownerAccountId.equals(optionalId(accountId))) {
            throw invalid("没有权限管理这个 Agent");
        }
    }

    public String id() { return identity.id(); }
    public Author identity() { return identity; }
    public String ownerAccountId() { return ownerAccountId; }
    public boolean communityOwned() { return ownerAccountId != null; }
    public String systemPrompt() { return systemPrompt; }
    public String model() { return model; }
    public double temperature() { return temperature; }
    public boolean canProcessPrivate() { return canProcessPrivate; }
    public boolean enabledRequested() { return enabledRequested; }
    public boolean enabled() { return identity.status() == AuthorStatus.ACTIVE; }
    public AgentReviewStatus reviewStatus() { return reviewStatus; }
    public String reviewNote() { return reviewNote; }
    public Instant reviewedAt() { return reviewedAt; }
    public boolean autoCommentEnabled() { return autoCommentEnabled; }
    public boolean autoCommentAllPosts() { return autoCommentAllPosts; }
    public List<String> autoCommentTags() { return autoCommentTags; }
    public long promptVersion() { return promptVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public String getId() { return id(); }
    public Author getIdentity() { return identity(); }
    public String getOwnerAccountId() { return ownerAccountId(); }
    public String getSystemPrompt() { return systemPrompt(); }
    public String getModel() { return model(); }
    public double getTemperature() { return temperature(); }
    public boolean isCanProcessPrivate() { return canProcessPrivate(); }
    public boolean isEnabledRequested() { return enabledRequested(); }
    public AgentReviewStatus getReviewStatus() { return reviewStatus(); }
    public String getReviewNote() { return reviewNote(); }
    public Instant getReviewedAt() { return reviewedAt(); }
    public boolean isAutoCommentEnabled() { return autoCommentEnabled(); }
    public boolean isAutoCommentAllPosts() { return autoCommentAllPosts(); }
    public List<String> getAutoCommentTags() { return autoCommentTags(); }
    public long getPromptVersion() { return promptVersion(); }
    public Instant getCreatedAt() { return createdAt(); }
    public Instant getUpdatedAt() { return updatedAt(); }

    private AgentProfile copy(
            Author nextIdentity,
            String nextOwner,
            String nextPrompt,
            String nextModel,
            double nextTemperature,
            boolean nextCanPrivate,
            boolean nextEnabledRequested,
            AgentReviewStatus nextReviewStatus,
            String nextReviewNote,
            Instant nextReviewedAt,
            boolean nextAutoEnabled,
            boolean nextAutoAll,
            List<String> nextAutoTags,
            Instant now) {
        return new AgentProfile(nextIdentity, nextOwner, nextPrompt, nextModel, nextTemperature,
                nextCanPrivate, nextEnabledRequested, nextReviewStatus, nextReviewNote, nextReviewedAt,
                nextAutoEnabled, nextAutoAll, nextAutoTags, promptVersion + 1, createdAt,
                Objects.requireNonNull(now, "now"));
    }

    private void validateOwnershipAndReviewState() {
        if (!communityOwned()) {
            if (reviewStatus != AgentReviewStatus.APPROVED || autoCommentEnabled || autoCommentAllPosts
                    || !autoCommentTags.isEmpty()) {
                throw invalid("站长 Agent 的审核或自动评论配置无效");
            }
            return;
        }
        if (canProcessPrivate || model != null) {
            throw invalid("会员 Agent 不能读取私密文章或指定模型");
        }
        boolean shouldBeActive = reviewStatus == AgentReviewStatus.APPROVED && enabledRequested;
        if (enabled() != shouldBeActive) {
            throw invalid("会员 Agent 的启用状态与审核状态不一致");
        }
        if (reviewStatus == AgentReviewStatus.PENDING && (reviewNote != null || reviewedAt != null)) {
            throw invalid("待审核 Agent 不能带有审核结果");
        }
        if (reviewStatus == AgentReviewStatus.REJECTED && (reviewNote == null || reviewedAt == null)) {
            throw invalid("被拒绝 Agent 必须包含审核说明");
        }
        if (reviewStatus == AgentReviewStatus.APPROVED && reviewedAt == null) {
            throw invalid("审核通过 Agent 必须记录审核时间");
        }
        if (autoCommentEnabled && !autoCommentAllPosts && autoCommentTags.isEmpty()) {
            throw invalid("自动评论需要选择全部文章或至少一个标签");
        }
    }

    private static Author identity(
            String id, String username, String displayName, String avatarUrl, boolean enabled) {
        return new Author(id, username, displayName, AuthorType.AGENT, avatarUrl,
                enabled ? AuthorStatus.ACTIVE : AuthorStatus.DISABLED);
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

    private static String optionalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 36) {
            throw invalid("账号 ID 无效");
        }
        return normalized;
    }

    private static List<String> normalizeTags(List<String> values) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > MAX_AUTO_TAGS) {
            throw invalid("自动评论标签不能超过 20 个");
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String value : source) {
            String tag = normalizeTag(value);
            if (tag.isEmpty() || tag.codePointCount(0, tag.length()) > 40) {
                throw invalid("自动评论标签长度无效");
            }
            tags.add(tag);
        }
        return List.copyOf(tags);
    }

    private static String normalizeTag(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static DomainException invalid(String message) {
        return new DomainException(DomainErrorCode.INVALID_AGENT, message);
    }
}
