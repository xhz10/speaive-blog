package com.speaive.blog.application.service;

import com.speaive.blog.application.command.agent.ConfigureAgentAutomationCommand;
import com.speaive.blog.application.command.agent.CreateAgentCommand;
import com.speaive.blog.application.command.agent.CreateOwnedAgentCommand;
import com.speaive.blog.application.command.agent.UpdateAgentCommand;
import com.speaive.blog.application.command.agent.UpdateOwnedAgentCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.agent.AgentUseCase;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.persistence.AccountRepository;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.agent.AgentListResult;
import com.speaive.blog.application.result.agent.AgentResult;
import com.speaive.blog.domain.account.MemberAccount;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.error.DomainException;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 站长与会员 Agent 管理用例：查询、创建、修改、审核和运行偏好配置。归属与审核不变量交给 AgentProfile 校验。
 */
public final class AgentApplicationService implements AgentUseCase {
    private final AgentRepository agents;
    private final AccountRepository accounts;
    private final AiCommentGenerationPort ai;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final int maxAgentsPerAccount;

    public AgentApplicationService(
            AgentRepository agents,
            AccountRepository accounts,
            AiCommentGenerationPort ai,
            TransactionRunner transactions,
            Clock clock,
            int maxAgentsPerAccount) {
        this.agents = Objects.requireNonNull(agents, "agents");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.ai = Objects.requireNonNull(ai, "ai");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxAgentsPerAccount < 1 || maxAgentsPerAccount > 20) {
            throw new IllegalArgumentException("每个会员的 Agent 上限必须为 1 到 20");
        }
        this.maxAgentsPerAccount = maxAgentsPerAccount;
    }

    @Override
    public AgentListResult listAgents() {
        return withDomainErrors(() -> transactions.required(() -> list(agents.findAll())));
    }

    @Override
    public AgentResult createAgent(CreateAgentCommand command) {
        if (command == null) {
            throw invalid("Agent 配置不能为空");
        }
        return withDomainErrors(() -> transactions.required(() -> {
            AgentProfile agent = AgentProfile.create(
                    UUID.randomUUID().toString(), command.username(), command.displayName(), command.avatarUrl(),
                    command.systemPrompt(), command.model(), command.temperature(), command.canProcessPrivate(),
                    command.enabled(), clock.instant());
            agents.add(agent);
            return result(agent);
        }));
    }

    @Override
    public AgentResult updateAgent(String id, UpdateAgentCommand command) {
        if (command == null) {
            throw invalid("Agent 配置不能为空");
        }
        return withDomainErrors(() -> transactions.required(() -> {
            AgentProfile current = requiredAgent(id);
            assertVersion(current, command.version());
            AgentProfile updated = current.update(
                    command.displayName(), command.avatarUrl(), command.systemPrompt(), command.model(),
                    command.temperature(), command.canProcessPrivate(), command.enabled(), clock.instant());
            agents.save(updated, current.promptVersion());
            return result(updated);
        }));
    }

    @Override
    public AgentListResult listOwnedAgents(String accountUsername) {
        return withDomainErrors(() -> transactions.required(() -> {
            MemberAccount account = requiredAccount(accountUsername);
            return list(agents.findByOwnerAccountId(account.id()));
        }));
    }

    @Override
    public AgentResult createOwnedAgent(String accountUsername, CreateOwnedAgentCommand command) {
        if (command == null) {
            throw invalid("Agent 配置不能为空");
        }
        return withDomainErrors(() -> transactions.required(() -> {
            MemberAccount account = accounts.lockByUsername(accountUsername)
                    .filter(MemberAccount::enabled)
                    .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND,
                            "会员账号不存在或已停用"));
            if (agents.findByOwnerAccountId(account.id()).size() >= maxAgentsPerAccount) {
                throw invalid("每个会员最多创建 " + maxAgentsPerAccount + " 个 Agent");
            }
            AgentProfile agent = AgentProfile.createOwned(
                    UUID.randomUUID().toString(), account.id(), command.username(), command.displayName(),
                    command.avatarUrl(), command.systemPrompt(), command.temperature(), command.autoCommentEnabled(),
                    command.autoCommentAllPosts(), command.autoCommentTags(), clock.instant());
            agents.add(agent);
            return result(agent);
        }));
    }

    @Override
    public AgentResult updateOwnedAgent(
            String accountUsername, String id, UpdateOwnedAgentCommand command) {
        if (command == null) {
            throw invalid("Agent 配置不能为空");
        }
        return withDomainErrors(() -> transactions.required(() -> {
            MemberAccount account = requiredAccount(accountUsername);
            AgentProfile current = requiredAgent(id);
            current.ensureOwnedBy(account.id());
            assertVersion(current, command.version());
            AgentProfile updated = current.updateOwnedProfile(
                    account.id(), command.displayName(), command.avatarUrl(), command.systemPrompt(),
                    command.temperature(), clock.instant());
            agents.save(updated, current.promptVersion());
            return result(updated);
        }));
    }

    @Override
    public AgentResult configureOwnedAgent(
            String accountUsername, String id, ConfigureAgentAutomationCommand command) {
        if (command == null) {
            throw invalid("Agent 自动评论配置不能为空");
        }
        return withDomainErrors(() -> transactions.required(() -> {
            MemberAccount account = requiredAccount(accountUsername);
            AgentProfile current = requiredAgent(id);
            current.ensureOwnedBy(account.id());
            assertVersion(current, command.version());
            AgentProfile updated = current.configureAutomation(
                    account.id(), command.enabled(), command.autoCommentEnabled(), command.autoCommentAllPosts(),
                    command.autoCommentTags(), clock.instant());
            agents.save(updated, current.promptVersion());
            return result(updated);
        }));
    }

    @Override
    public AgentResult approveAgent(String id, long version) {
        return review(id, version, null, true);
    }

    @Override
    public AgentResult rejectAgent(String id, long version, String note) {
        return review(id, version, note, false);
    }

    private AgentResult review(String id, long version, String note, boolean approve) {
        return withDomainErrors(() -> transactions.required(() -> {
            AgentProfile current = requiredAgent(id);
            assertVersion(current, version);
            AgentProfile updated = approve ? current.approve(clock.instant()) : current.reject(note, clock.instant());
            agents.save(updated, current.promptVersion());
            return result(updated);
        }));
    }

    private AgentProfile requiredAgent(String id) {
        return agents.findById(id)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "Agent 不存在"));
    }

    private MemberAccount requiredAccount(String username) {
        return accounts.findByUsername(username)
                .filter(MemberAccount::enabled)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "会员账号不存在或已停用"));
    }

    private AgentListResult list(java.util.List<AgentProfile> items) {
        return new AgentListResult(items.stream().map(AgentApplicationService::result).toList(), ai.isAvailable());
    }

    private static void assertVersion(AgentProfile agent, long version) {
        if (agent.promptVersion() != version) {
            throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "Agent 已在其他位置更新，请刷新后重试");
        }
    }

    private static AgentResult result(AgentProfile agent) {
        return new AgentResult(
                agent.id(), agent.identity().username(), agent.identity().displayName(), agent.identity().avatarUrl(),
                agent.ownerAccountId(), agent.systemPrompt(), agent.model(), agent.temperature(),
                agent.canProcessPrivate(), agent.enabled(), agent.enabledRequested(), agent.reviewStatus().name(),
                agent.reviewNote(), agent.reviewedAt(), agent.autoCommentEnabled(), agent.autoCommentAllPosts(),
                agent.autoCommentTags(), agent.promptVersion(), agent.createdAt(), agent.updatedAt());
    }

    private static BlogException invalid(String message) {
        return new BlogException(BlogErrorCode.INVALID_REQUEST, message);
    }

    private static <T> T withDomainErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (DomainException exception) {
            throw invalid(exception.getMessage());
        }
    }
}
