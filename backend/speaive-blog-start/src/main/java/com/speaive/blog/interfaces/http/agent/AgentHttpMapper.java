package com.speaive.blog.interfaces.http.agent;

import com.speaive.blog.application.command.agent.CreateAgentCommand;
import com.speaive.blog.application.command.agent.CreateOwnedAgentCommand;
import com.speaive.blog.application.command.agent.ConfigureAgentAutomationCommand;
import com.speaive.blog.application.command.agent.UpdateAgentCommand;
import com.speaive.blog.application.command.agent.UpdateOwnedAgentCommand;
import com.speaive.blog.application.result.agent.AgentListResult;
import com.speaive.blog.application.result.agent.AgentResult;
import com.speaive.blog.interfaces.http.agent.AgentRequests.CreateAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentRequests.CreateOwnedAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentRequests.ConfigureOwnedAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentRequests.UpdateAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentRequests.UpdateOwnedAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentResponses.AgentDetail;
import com.speaive.blog.interfaces.http.agent.AgentResponses.AgentList;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface AgentHttpMapper {
    CreateAgentCommand toCommand(CreateAgentRequest request);

    UpdateAgentCommand toCommand(UpdateAgentRequest request);

    CreateOwnedAgentCommand toCommand(CreateOwnedAgentRequest request);

    UpdateOwnedAgentCommand toCommand(UpdateOwnedAgentRequest request);

    ConfigureAgentAutomationCommand toCommand(ConfigureOwnedAgentRequest request);

    AgentDetail toResponse(AgentResult result);

    AgentList toResponse(AgentListResult result);
}
