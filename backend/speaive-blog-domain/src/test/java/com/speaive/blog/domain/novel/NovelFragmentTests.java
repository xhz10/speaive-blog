package com.speaive.blog.domain.novel;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NovelFragmentTests {
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T08:00:00Z");
    private static final Author AUTHOR = new Author(
            "author-id", "author", "Author", AuthorType.HUMAN, null, AuthorStatus.ACTIVE);

    @Test
    void newFragmentsDefaultToPrivateDrafts() {
        NovelFragment fragment = NovelFragment.createDraft(
                "fragment-id", NovelFragmentSlug.of("rainy-platform"), content("雨夜站台"), AUTHOR, CREATED_AT);

        assertAll(
                () -> assertEquals(NovelFragmentStatus.DRAFT, fragment.status()),
                () -> assertEquals(NovelFragmentVisibility.ADMIN_ONLY, fragment.visibility()),
                () -> assertNull(fragment.publishedAt()),
                () -> assertEquals("fragment-id:1", fragment.version()),
                () -> assertEquals(fragment.snapshot(), NovelFragment.rehydrate(fragment.snapshot()).snapshot()));
    }

    @Test
    void updatePublishAndUnpublishKeepAnAuditableRevisionChain() {
        NovelFragment draft = NovelFragment.createDraft(
                "fragment-id", NovelFragmentSlug.of("rainy-platform"), content("雨夜站台"), AUTHOR,
                NovelFragmentVisibility.ADMIN_ONLY, CREATED_AT);
        NovelFragmentChange updated = draft.update(
                content("雨夜站台（二稿）"), NovelFragmentVisibility.PUBLIC,
                draft.version(), CREATED_AT.plusSeconds(1));
        NovelFragmentChange published = updated.current().publish(
                updated.current().version(), CREATED_AT.plusSeconds(2));
        NovelFragmentChange unpublished = published.current().unpublish(
                published.current().version(), CREATED_AT.plusSeconds(3));

        assertAll(
                () -> assertEquals(NovelFragmentRevisionEventType.UPDATE, updated.eventType()),
                () -> assertEquals(NovelFragmentVisibility.PUBLIC, updated.current().visibility()),
                () -> assertEquals("fragment-id:2", updated.current().version()),
                () -> assertEquals(NovelFragmentRevisionEventType.PUBLISH, published.eventType()),
                () -> assertEquals(NovelFragmentStatus.PUBLISHED, published.current().status()),
                () -> assertEquals(CREATED_AT.plusSeconds(2), published.current().publishedAt()),
                () -> assertEquals("fragment-id:3", published.current().version()),
                () -> assertEquals(NovelFragmentRevisionEventType.UNPUBLISH, unpublished.eventType()),
                () -> assertEquals(NovelFragmentStatus.DRAFT, unpublished.current().status()),
                () -> assertEquals("fragment-id:4", unpublished.current().version()));
    }

    @Test
    void staleEditorsCannotOverwriteNewerFragments() {
        NovelFragment draft = NovelFragment.createDraft(
                "fragment-id", NovelFragmentSlug.of("rainy-platform"), content("雨夜站台"), AUTHOR, CREATED_AT);

        DomainException exception = assertThrows(DomainException.class, () -> draft.update(
                content("旧页面内容"), NovelFragmentVisibility.PUBLIC,
                "fragment-id:0", CREATED_AT.plusSeconds(1)));

        assertEquals(DomainErrorCode.VERSION_CONFLICT, exception.code());
    }

    private static NovelFragmentContent content(String title) {
        return new NovelFragmentContent(title, "一句话简介", "雨落在站台上。\n\n她没有上车。");
    }
}
