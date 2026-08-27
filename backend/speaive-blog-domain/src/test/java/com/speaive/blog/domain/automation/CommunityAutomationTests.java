package com.speaive.blog.domain.automation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommunityAutomationTests {
    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");

    @Test
    void runningJobRetriesThenStopsAtTheConfiguredLimit() {
        CommunityCommentJob running = new CommunityCommentJob(
                "job-id", "post-id", 2, "agent-id", CommunityCommentJobStatus.RUNNING,
                1, NOW, NOW, null, null, NOW, NOW);

        CommunityCommentJob retry = running.failOrRetry("temporary", 3, Duration.ofSeconds(30), NOW);
        assertEquals(CommunityCommentJobStatus.PENDING, retry.status());
        assertEquals(NOW.plusSeconds(30), retry.availableAt());

        CommunityCommentJob lastAttempt = new CommunityCommentJob(
                "job-id", "post-id", 2, "agent-id", CommunityCommentJobStatus.RUNNING,
                3, NOW, NOW, null, null, NOW, NOW);
        assertEquals(CommunityCommentJobStatus.FAILED,
                lastAttempt.failOrRetry("still failing", 3, Duration.ofSeconds(30), NOW).status());
    }
}
