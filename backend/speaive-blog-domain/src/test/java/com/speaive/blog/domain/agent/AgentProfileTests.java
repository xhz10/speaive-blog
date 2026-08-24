package com.speaive.blog.domain.agent;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.PostVisibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProfileTests {
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T01:00:00Z");

    @Test
    void createsEnabledAgentAndAllowsPublicGeneration() {
        AgentProfile agent = createAgent(false, true);

        assertTrue(agent.enabled());
        assertEquals(1, agent.promptVersion());
        agent.ensureCanGenerate(PostVisibility.PUBLIC);
    }

    @Test
    void protectsPrivateArticlesFromAgentsWithoutPermission() {
        AgentProfile agent = createAgent(false, true);

        DomainException exception = assertThrows(
                DomainException.class,
                () -> agent.ensureCanGenerate(PostVisibility.ADMIN_ONLY));

        assertEquals(DomainErrorCode.INVALID_AGENT, exception.code());
    }

    @Test
    void updatingAgentAdvancesConfigurationVersionAndCanGrantPrivatePermission() {
        AgentProfile updated = createAgent(false, true).update(
                "认真读者",
                null,
                "请认真阅读文章并给出具体反馈。",
                "gpt-4.1-mini",
                0.4,
                true,
                false,
                CREATED_AT.plusSeconds(60));

        assertEquals(2, updated.promptVersion());
        assertFalse(updated.enabled());
        assertTrue(updated.canProcessPrivate());
        assertEquals("请认真阅读文章并给出具体反馈。", updated.systemPrompt());
        assertEquals("gpt-4.1-mini", updated.model());
    }

    @Test
    void rejectsInvalidTemperatureAndBlankPrompt() {
        assertInvalidAgent(() -> AgentProfile.create(
                "agent-id", "reader", "读者", null, "角色提示词", null,
                2.1, false, true, CREATED_AT));
        assertInvalidAgent(() -> AgentProfile.create(
                "agent-id", "reader", "读者", null, " ", null,
                0.7, false, true, CREATED_AT));
    }

    @Test
    void truncatesLongProviderErrorsWhenFinishingARun() {
        AgentRun failed = AgentRun.start(
                        "run-id", "post-id", 1, "agent-id", 1, null, CREATED_AT)
                .fail("错误".repeat(300), CREATED_AT.plusSeconds(1));

        assertEquals(500, failed.errorMessage().length());
        assertEquals(AgentRunStatus.FAILED, failed.status());
    }

    private static AgentProfile createAgent(boolean canProcessPrivate, boolean enabled) {
        return AgentProfile.create(
                "agent-id",
                "reader",
                "认真读者",
                null,
                "结合文章内容写一条自然、具体的评论。",
                null,
                0.7,
                canProcessPrivate,
                enabled,
                CREATED_AT);
    }

    private static void assertInvalidAgent(org.junit.jupiter.api.function.Executable executable) {
        DomainException exception = assertThrows(DomainException.class, executable);
        assertEquals(DomainErrorCode.INVALID_AGENT, exception.code());
    }
}
