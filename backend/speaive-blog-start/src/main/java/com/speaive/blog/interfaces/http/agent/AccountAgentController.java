package com.speaive.blog.interfaces.http.agent;

import com.speaive.blog.application.port.in.agent.AgentUseCase;
import com.speaive.blog.interfaces.http.agent.AgentRequests.ConfigureOwnedAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentRequests.CreateOwnedAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentRequests.UpdateOwnedAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentResponses.AgentDetail;
import com.speaive.blog.interfaces.http.agent.AgentResponses.AgentList;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account/agents")
public class AccountAgentController {
    private final AgentUseCase agents;
    private final AgentHttpMapper mapper;

    public AccountAgentController(AgentUseCase agents, AgentHttpMapper mapper) {
        this.agents = agents;
        this.mapper = mapper;
    }

    @GetMapping
    AgentList list(Authentication authentication) {
        return mapper.toResponse(agents.listOwnedAgents(authentication.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AgentDetail create(
            Authentication authentication,
            @Valid @RequestBody CreateOwnedAgentRequest request) {
        return mapper.toResponse(agents.createOwnedAgent(authentication.getName(), mapper.toCommand(request)));
    }

    @PutMapping("/{id}")
    AgentDetail update(
            Authentication authentication,
            @PathVariable String id,
            @Valid @RequestBody UpdateOwnedAgentRequest request) {
        return mapper.toResponse(agents.updateOwnedAgent(
                authentication.getName(), id, mapper.toCommand(request)));
    }

    @PutMapping("/{id}/automation")
    AgentDetail configure(
            Authentication authentication,
            @PathVariable String id,
            @Valid @RequestBody ConfigureOwnedAgentRequest request) {
        return mapper.toResponse(agents.configureOwnedAgent(
                authentication.getName(), id, mapper.toCommand(request)));
    }
}
