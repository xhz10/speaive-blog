package com.speaive.blog.application.service;

import com.speaive.blog.application.command.agent.CreateAgentCommand;
import com.speaive.blog.application.command.agent.UpdateAgentCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.agent.AgentUseCase;
import com.speaive.blog.application.port.out.ai.AiCommentGenerationPort;
import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.agent.AgentListResult;
import com.speaive.blog.application.result.agent.AgentResult;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.error.DomainException;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class AgentApplicationService implements AgentUseCase {
    private final AgentRepository agents;
    private final AiCommentGenerationPort ai;
    private final TransactionRunner transactions;
    private final Clock clock;

    public AgentApplicationService(
            AgentRepository agents,
            AiCommentGenerationPort ai,
            TransactionRunner transactions,
            Clock clock) {
        this.agents = Objects.requireNonNull(agents, "agents");
        this.ai = Objects.requireNonNull(ai, "ai");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AgentListResult listAgents() {
        return withDomainErrors(() -> transactions.required(() -> new AgentListResult(
                agents.findAll().stream().map(AgentApplicationService::result).toList(),
                ai.isAvailable())));
    }

    @Override
    public AgentResult createAgent(CreateAgentCommand command) {
        if (command == null) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "Agent 配置不能为空");
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
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, "Agent 配置不能为空");
        }
        return withDomainErrors(() -> transactions.required(() -> {
            AgentProfile current = agents.findById(id)
                    .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "Agent 不存在"));
            if (current.promptVersion() != command.version()) {
                throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "Agent 已在其他位置更新，请刷新后重试");
            }
            AgentProfile updated = current.update(
                    command.displayName(), command.avatarUrl(), command.systemPrompt(), command.model(),
                    command.temperature(), command.canProcessPrivate(), command.enabled(), clock.instant());
            agents.save(updated, current.promptVersion());
            return result(updated);
        }));
    }

    private static AgentResult result(AgentProfile agent) {
        return new AgentResult(
                agent.id(), agent.identity().username(), agent.identity().displayName(), agent.identity().avatarUrl(),
                agent.systemPrompt(), agent.model(), agent.temperature(), agent.canProcessPrivate(), agent.enabled(),
                agent.promptVersion(), agent.createdAt(), agent.updatedAt());
    }

    private static <T> T withDomainErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (DomainException exception) {
            throw new BlogException(BlogErrorCode.INVALID_REQUEST, exception.getMessage(), exception);
        }
    }
}
