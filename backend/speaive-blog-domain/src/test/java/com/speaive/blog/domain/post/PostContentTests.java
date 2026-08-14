package com.speaive.blog.domain.post;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostContentTests {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-06T01:00:00Z");

    @Test
    void normalizesTextTagsCoverAndBody() {
        PostContent content = new PostContent(
                "  标题  ",
                null,
                PUBLISHED_AT,
                List.of(" Java ", "DDD", "Java"),
                "  /media/covers/post.webp  ",
                "  正文  ");

        assertEquals("标题", content.title());
        assertEquals("", content.description());
        assertEquals(List.of("Java", "DDD"), content.tags());
        assertEquals("/media/covers/post.webp", content.cover());
        assertEquals("正文", content.body());

        PostContent withoutOptionalFields = new PostContent(
                "标题", "", PUBLISHED_AT, null, " ", "");
        assertEquals(List.of(), withoutOptionalFields.tags());
        assertNull(withoutOptionalFields.cover());
    }

    @Test
    void rejectsInvalidRequiredAndOversizedContent() {
        assertInvalidContent(() -> content(" ", "", List.of(), null, "body"));
        assertInvalidContent(() -> content("x".repeat(PostContent.MAX_TITLE_LENGTH + 1),
                "", List.of(), null, "body"));
        assertInvalidContent(() -> content("title", "x".repeat(PostContent.MAX_DESCRIPTION_LENGTH + 1),
                List.of(), null, "body"));
        assertInvalidContent(() -> new PostContent("title", "", null, List.of(), null, "body"));
        assertInvalidContent(() -> content("title", "", List.of(), null, null));
        assertInvalidContent(() -> content("title", "", List.of(), null, "bad\0body"));
    }

    @Test
    void rejectsInvalidTagsAndCover() {
        List<String> tooManyTags = new ArrayList<>();
        for (int index = 0; index <= PostContent.MAX_TAG_COUNT; index++) {
            tooManyTags.add("tag-" + index);
        }

        assertInvalidContent(() -> content("title", "", tooManyTags, null, "body"));
        assertInvalidContent(() -> content("title", "", List.of(" "), null, "body"));
        assertInvalidContent(() -> content("title", "",
                List.of("x".repeat(PostContent.MAX_TAG_LENGTH + 1)), null, "body"));
        assertInvalidContent(() -> content("title", "", List.of(), "https://example.com/cover.png", "body"));
        assertInvalidContent(() -> content("title", "", List.of(), "/media/../secret.png", "body"));
    }

    private static PostContent content(
            String title, String description, List<String> tags, String cover, String body) {
        return new PostContent(title, description, PUBLISHED_AT, tags, cover, body);
    }

    private static void assertInvalidContent(org.junit.jupiter.api.function.Executable executable) {
        DomainException exception = assertThrows(DomainException.class, executable);
        assertEquals(DomainErrorCode.INVALID_CONTENT, exception.code());
    }
}
