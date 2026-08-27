package com.speaive.blog.application.service;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.automation.CommunityAutomationUseCase;
import com.speaive.blog.application.port.in.comment.CommentUseCase;
import com.speaive.blog.application.port.out.persistence.CommunityCommentJobRepository;
import com.speaive.blog.application.port.out.persistence.CommunityPostPolicyRepository;
import com.speaive.blog.application.port.out.persistence.PostQueryScope;
import com.speaive.blog.application.port.out.persistence.PostRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.automation.AutomationBatchResult;
import com.speaive.blog.application.result.automation.PostCommunityPolicyResult;
import com.speaive.blog.domain.automation.CommunityCommentJob;
import com.speaive.blog.domain.automation.CommunityCommentJobStatus;
import com.speaive.blog.domain.automation.CommunityPostPolicy;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostVisibility;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

public final class CommunityAutomationApplicationService implements CommunityAutomationUseCase {
    private static final Duration STALE_JOB_AFTER = Duration.ofMinutes(15);

    private final PostRepository posts;
    private final CommunityPostPolicyRepository policies;
    private final CommunityCommentJobRepository jobs;
    private final CommunityAutomationPlanner planner;
    private final CommentUseCase comments;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final int maxAttempts;

    public CommunityAutomationApplicationService(
            PostRepository posts,
            CommunityPostPolicyRepository policies,
            CommunityCommentJobRepository jobs,
            CommunityAutomationPlanner planner,
            CommentUseCase comments,
            TransactionRunner transactions,
            Clock clock,
            int maxAttempts) {
        this.posts = Objects.requireNonNull(posts, "posts");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.comments = Objects.requireNonNull(comments, "comments");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("自动评论最大尝试次数必须为 1 到 10");
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public PostCommunityPolicyResult getPostPolicy(String postSlug) {
        return withDomainErrors(() -> transactions.required(() -> {
            Post post = requiredPost(postSlug);
            return policies.findByPostId(post.id())
                    .map(policy -> result(post, policy, 0))
                    .orElseGet(() -> new PostCommunityPolicyResult(post.slug(), false, 0, 0));
        }));
    }

    @Override
    public PostCommunityPolicyResult updatePostPolicy(
            String postSlug, boolean enabled, long expectedVersion) {
        return withDomainErrors(() -> transactions.required(() -> {
            Post post = requiredPost(postSlug);
            if (enabled && post.visibility() != PostVisibility.PUBLIC) {
                throw new BlogException(BlogErrorCode.INVALID_REQUEST,
                        "私密文章不能开放给社区 Agent");
            }
            Instant now = clock.instant();
            CommunityPostPolicy current = policies.lockByPostId(post.id()).orElse(null);
            CommunityPostPolicy updated;
            if (current == null) {
                if (expectedVersion != 0) {
                    throw new BlogException(BlogErrorCode.VERSION_CONFLICT,
                            "社区 Agent 设置已更新，请刷新后重试");
                }
                updated = new CommunityPostPolicy(post.id(), enabled, 1, now, now);
                policies.add(updated);
            } else {
                updated = current.update(enabled, expectedVersion, now);
                policies.save(updated, current.version());
            }
            int queued = updated.enabled() ? planner.plan(post) : 0;
            return result(post, updated, queued);
        }));
    }

    @Override
    public AutomationBatchResult processDueJobs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        int processed = 0;
        int succeeded = 0;
        int skipped = 0;
        int retried = 0;
        int failed = 0;
        for (int index = 0; index < safeLimit; index++) {
            Instant now = clock.instant();
            CommunityCommentJob job = transactions.required(() ->
                    jobs.claimNext(now, now.minus(STALE_JOB_AFTER)).orElse(null));
            if (job == null) {
                break;
            }
            processed++;
            try {
                comments.generateAutomatedAiComment(job.postId(), job.postRevision(), job.agentId());
                CommunityCommentJob completed = job.succeed(clock.instant());
                transactions.required(() -> { jobs.save(completed); return null; });
                succeeded++;
            } catch (RuntimeException exception) {
                if (shouldSkip(exception)) {
                    CommunityCommentJob completed = job.skip(message(exception), clock.instant());
                    transactions.required(() -> { jobs.save(completed); return null; });
                    skipped++;
                } else {
                    CommunityCommentJob completed = job.failOrRetry(
                            message(exception), maxAttempts, retryDelay(job.attempts()), clock.instant());
                    transactions.required(() -> { jobs.save(completed); return null; });
                    if (completed.status() == CommunityCommentJobStatus.FAILED) failed++;
                    else retried++;
                }
            }
        }
        return new AutomationBatchResult(processed, succeeded, skipped, retried, failed);
    }

    private Post requiredPost(String slug) {
        return posts.findBySlug(slug, PostQueryScope.STUDIO)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在"));
    }

    private static PostCommunityPolicyResult result(
            Post post, CommunityPostPolicy policy, int queuedAgents) {
        return new PostCommunityPolicyResult(post.slug(), policy.enabled(), policy.version(), queuedAgents);
    }

    private static boolean shouldSkip(RuntimeException exception) {
        if (!(exception instanceof BlogException blog)) {
            return false;
        }
        return switch (blog.code()) {
            case NOT_FOUND, INVALID_REQUEST, VERSION_CONFLICT, GENERATION_CONFLICT -> true;
            default -> false;
        };
    }

    private static Duration retryDelay(int attempts) {
        long seconds = 30L * (1L << Math.min(Math.max(attempts - 1, 0), 5));
        return Duration.ofSeconds(seconds);
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static <T> T withDomainErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (DomainException exception) {
            BlogErrorCode code = exception.code() == com.speaive.blog.domain.error.DomainErrorCode.VERSION_CONFLICT
                    ? BlogErrorCode.VERSION_CONFLICT
                    : BlogErrorCode.INVALID_REQUEST;
            throw new BlogException(code, exception.getMessage(), exception);
        }
    }
}
