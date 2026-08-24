package com.speaive.blog.domain.comment;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommentTests {
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T01:00:00Z");

    @Test
    void aiCommentStartsPendingAndRequiresExplicitPublication() {
        Comment pending = Comment.createAiCandidate(
                "comment-id", "post-id", activeAgent(), "  这段经历写得很真实。  ", CREATED_AT);
        Comment published = pending.publish(CREATED_AT.plusSeconds(1));
        Comment hidden = published.hide(CREATED_AT.plusSeconds(2));

        assertEquals("这段经历写得很真实。", pending.body());
        assertEquals(CommentStatus.PENDING, pending.status());
        assertEquals(CommentStatus.PUBLISHED, published.status());
        assertEquals(CommentStatus.HIDDEN, hidden.status());
    }

    @Test
    void hiddenCommentCannotBePublishedAgain() {
        Comment hidden = Comment.createAiCandidate(
                "comment-id", "post-id", activeAgent(), "一条待审核评论", CREATED_AT)
                .hide(CREATED_AT.plusSeconds(1));

        assertInvalidComment(() -> hidden.publish(CREATED_AT.plusSeconds(2)));
    }

    @Test
    void requiresAnEnabledAgentAuthor() {
        Author human = new Author(
                "admin-id", "admin", "管理员", AuthorType.HUMAN, null, AuthorStatus.ACTIVE);
        Author disabledAgent = new Author(
                "agent-id", "reader", "读者", AuthorType.AGENT, null, AuthorStatus.DISABLED);

        assertInvalidComment(() -> Comment.createAiCandidate(
                "comment-id", "post-id", human, "评论", CREATED_AT));
        assertInvalidComment(() -> Comment.createAiCandidate(
                "comment-id", "post-id", disabledAgent, "评论", CREATED_AT));
    }

    @Test
    void rejectsBlankAndOversizedBody() {
        assertInvalidComment(() -> Comment.createAiCandidate(
                "comment-id", "post-id", activeAgent(), " ", CREATED_AT));
        assertInvalidComment(() -> Comment.createAiCandidate(
                "comment-id", "post-id", activeAgent(), "字".repeat(2_001), CREATED_AT));
    }

    private static Author activeAgent() {
        return new Author(
                "agent-id", "reader", "认真读者", AuthorType.AGENT, null, AuthorStatus.ACTIVE);
    }

    private static void assertInvalidComment(org.junit.jupiter.api.function.Executable executable) {
        DomainException exception = assertThrows(DomainException.class, executable);
        assertEquals(DomainErrorCode.INVALID_COMMENT, exception.code());
    }
}
