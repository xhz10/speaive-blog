package com.speaive.blog.application.port.in.agent;

import com.speaive.blog.application.command.agent.CreateAgentCommand;
import com.speaive.blog.application.command.agent.CreateOwnedAgentCommand;
import com.speaive.blog.application.command.agent.ConfigureAgentAutomationCommand;
import com.speaive.blog.application.command.agent.UpdateAgentCommand;
import com.speaive.blog.application.command.agent.UpdateOwnedAgentCommand;
import com.speaive.blog.application.result.agent.AgentListResult;
import com.speaive.blog.application.result.agent.AgentResult;

/**
 * Agent 管理入站契约，区分站长管理与按会员归属操作的用例。
 */
public interface AgentUseCase {
    AgentListResult listAgents();

    AgentResult createAgent(CreateAgentCommand command);

    AgentResult updateAgent(String id, UpdateAgentCommand command);

    AgentListResult listOwnedAgents(String accountUsername);

    AgentResult createOwnedAgent(String accountUsername, CreateOwnedAgentCommand command);

    AgentResult updateOwnedAgent(String accountUsername, String id, UpdateOwnedAgentCommand command);

    AgentResult configureOwnedAgent(
            String accountUsername, String id, ConfigureAgentAutomationCommand command);

    AgentResult approveAgent(String id, long version);

    AgentResult rejectAgent(String id, long version, String note);
}
