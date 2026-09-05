package com.speaive.blog.application.service;

import com.speaive.blog.application.port.out.persistence.AgentRepository;
import com.speaive.blog.application.port.out.persistence.CommunityCommentJobRepository;
import com.speaive.blog.application.port.out.persistence.CommunityPostPolicyRepository;
import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.automation.CommunityCommentJob;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostStatus;
import com.speaive.blog.domain.post.PostVisibility;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * 社区评论入队的应用协作者，读取文章开关并按角色关注规则筛选候选人，再通过唯一键幂等登记任务。通常由现有事务内的发布或设置用例调用。
 */
public final class CommunityAutomationPlanner {
    private final AgentRepository agents;
    private final CommunityPostPolicyRepository policies;
    private final CommunityCommentJobRepository jobs;
    private final Clock clock;
    private final int maxAgentsPerPost;

    public CommunityAutomationPlanner(
            AgentRepository agents,
            CommunityPostPolicyRepository policies,
            CommunityCommentJobRepository jobs,
            Clock clock,
            int maxAgentsPerPost) {
        this.agents = Objects.requireNonNull(agents, "agents");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxAgentsPerPost < 1 || maxAgentsPerPost > 20) {
            throw new IllegalArgumentException("每篇文章自动 Agent 上限必须为 1 到 20");
        }
        this.maxAgentsPerPost = maxAgentsPerPost;
    }

    public int plan(Post post) {
        if (post.status() != PostStatus.PUBLISHED || post.visibility() != PostVisibility.PUBLIC
                || policies.findByPostId(post.id()).filter(policy -> policy.enabled()).isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        int queued = 0;
        for (AgentProfile agent : agents.findAll().stream()
                .filter(candidate -> candidate.matchesAutomaticTags(post.tags()))
                .sorted(Comparator.comparing(AgentProfile::createdAt).thenComparing(AgentProfile::id))
                .limit(maxAgentsPerPost)
                .toList()) {
            CommunityCommentJob job = CommunityCommentJob.create(
                    UUID.randomUUID().toString(), post.id(), post.revision(), agent.id(), now);
            if (jobs.addIfAbsent(job)) {
                queued++;
            }
        }
        return queued;
    }
}
