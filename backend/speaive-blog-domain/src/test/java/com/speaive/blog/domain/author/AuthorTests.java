package com.speaive.blog.domain.author;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostContent;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorTests {

    @Test
    void normalizesAnActiveAuthor() {
        Author author = new Author(
                " author-id ", " Agent-One ", " Agent One ", AuthorType.AGENT, " ", AuthorStatus.ACTIVE);

        assertEquals("author-id", author.id());
        assertEquals("agent-one", author.username());
        assertEquals("Agent One", author.displayName());
        assertNull(author.avatarUrl());
        assertTrue(author.canAuthor());
        assertEquals(author, author.ensureCanAuthor());
    }

    @Test
    void rejectsDisabledAuthorWhenCreatingContent() {
        Author disabled = new Author(
                "disabled-id", "disabled", "Disabled", AuthorType.AGENT, null, AuthorStatus.DISABLED);

        assertFalse(disabled.canAuthor());
        assertInvalidAuthor(disabled::ensureCanAuthor);
        assertInvalidAuthor(() -> Post.createDraft(
                "post-id", "disabled-post", validContent(), disabled, Instant.parse("2026-08-06T02:00:00Z")));
    }

    @Test
    void rejectsInvalidAuthorFields() {
        assertInvalidAuthor(() -> new Author(
                null, "author", "Author", AuthorType.HUMAN, null, AuthorStatus.ACTIVE));
        assertInvalidAuthor(() -> new Author(
                "author-id", " ", "Author", AuthorType.HUMAN, null, AuthorStatus.ACTIVE));
        assertInvalidAuthor(() -> new Author(
                "author-id", "author", " ", AuthorType.HUMAN, null, AuthorStatus.ACTIVE));
        assertInvalidAuthor(() -> new Author(
                "author-id", "author", "Author", null, null, AuthorStatus.ACTIVE));
        assertInvalidAuthor(() -> new Author(
                "author-id", "author", "Author", AuthorType.HUMAN, null, null));
    }

    private static PostContent validContent() {
        return new PostContent("Title", "", Instant.parse("2026-08-06T01:00:00Z"), null, null, "Body");
    }

    private static void assertInvalidAuthor(org.junit.jupiter.api.function.Executable executable) {
        DomainException exception = assertThrows(DomainException.class, executable);
        assertEquals(DomainErrorCode.INVALID_AUTHOR, exception.code());
    }
}
