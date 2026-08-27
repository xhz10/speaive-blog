package com.speaive.blog.interfaces.http.agent;

import com.speaive.blog.application.port.in.agent.AgentUseCase;
import com.speaive.blog.interfaces.http.agent.AgentRequests.CreateAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentRequests.UpdateAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentRequests.ReviewAgentRequest;
import com.speaive.blog.interfaces.http.agent.AgentResponses.AgentDetail;
import com.speaive.blog.interfaces.http.agent.AgentResponses.AgentList;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studio/agents")
public class StudioAgentController {
    private final AgentUseCase agents;
    private final AgentHttpMapper mapper;

    public StudioAgentController(AgentUseCase agents, AgentHttpMapper mapper) {
        this.agents = agents;
        this.mapper = mapper;
    }

    @GetMapping
    AgentList list() {
        return mapper.toResponse(agents.listAgents());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AgentDetail create(@Valid @RequestBody CreateAgentRequest request) {
        return mapper.toResponse(agents.createAgent(mapper.toCommand(request)));
    }

    @PutMapping("/{id}")
    AgentDetail update(@PathVariable String id, @Valid @RequestBody UpdateAgentRequest request) {
        return mapper.toResponse(agents.updateAgent(id, mapper.toCommand(request)));
    }

    @PostMapping("/{id}/approve")
    AgentDetail approve(@PathVariable String id, @Valid @RequestBody ReviewAgentRequest request) {
        return mapper.toResponse(agents.approveAgent(id, request.version()));
    }

    @PostMapping("/{id}/reject")
    AgentDetail reject(@PathVariable String id, @Valid @RequestBody ReviewAgentRequest request) {
        return mapper.toResponse(agents.rejectAgent(id, request.version(), request.note()));
    }
}
