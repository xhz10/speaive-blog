package com.speaive.blog.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostSlugTests {

    @Test
    void normalizesWhitespaceAndUnicode() {
        PostSlug slug = PostSlug.of("  cafe\u0301-文章  ");

        assertEquals("caf\u00e9-文章", slug.value());
        assertEquals(slug.value(), slug.toString());
    }

    @Test
    void rejectsMissingMalformedAndOversizedSlugs() {
        assertInvalidSlug(null);
        assertInvalidSlug(" ");
        assertInvalidSlug("two--dashes");
        assertInvalidSlug("-leading");
        assertInvalidSlug("trailing-");
        assertInvalidSlug("has spaces");
        assertInvalidSlug("a".repeat(PostSlug.MAX_LENGTH + 1));
    }

    private static void assertInvalidSlug(String value) {
        DomainException exception = assertThrows(DomainException.class, () -> PostSlug.of(value));
        assertEquals(DomainErrorCode.INVALID_SLUG, exception.code());
    }
}
