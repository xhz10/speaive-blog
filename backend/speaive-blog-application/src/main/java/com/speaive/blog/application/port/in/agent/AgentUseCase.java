package com.speaive.blog.application.port.in.agent;

import com.speaive.blog.application.command.agent.CreateAgentCommand;
import com.speaive.blog.application.command.agent.UpdateAgentCommand;
import com.speaive.blog.application.result.agent.AgentListResult;
import com.speaive.blog.application.result.agent.AgentResult;

public interface AgentUseCase {
    AgentListResult listAgents();

    AgentResult createAgent(CreateAgentCommand command);

    AgentResult updateAgent(String id, UpdateAgentCommand command);
}
