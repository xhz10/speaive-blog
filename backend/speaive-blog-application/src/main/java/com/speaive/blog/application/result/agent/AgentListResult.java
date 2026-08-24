package com.speaive.blog.application.result.agent;

import java.util.List;

public record AgentListResult(List<AgentResult> items, boolean aiAvailable) {
    public AgentListResult {
        items = List.copyOf(items);
    }
}
