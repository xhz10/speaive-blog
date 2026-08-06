package com.speaive.blog.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostTests {
    private static final Instant CREATED_AT = Instant.parse("2026-08-06T01:00:00Z");
    private static final Author AUTHOR = new Author(
            "author-id", "author", "Author", AuthorType.HUMAN, null, AuthorStatus.ACTIVE);

    @Test
    void createsDraftWithInitialVersionAndRoundTripsSnapshot() {
        Post post = draft();

        assertAll(
                () -> assertEquals("post-id", post.id()),
                () -> assertEquals("ddd-post", post.slug()),
                () -> assertEquals(PostStatus.DRAFT, post.status()),
                () -> assertEquals(1, post.revision()),
                () -> assertEquals("post-id:1", post.version()),
                () -> assertEquals(CREATED_AT, post.createdAt()),
                () -> assertEquals(CREATED_AT, post.updatedAt()),
                () -> assertFalse(post.archived()),
                () -> assertEquals(post, Post.rehydrate(post.snapshot())));
    }

    @Test
    void updatesContentWithoutChangingIdentityOrOriginalAggregate() {
        Post previous = draft();
        PostContent replacement = new PostContent(
                "Updated", "Updated description", CREATED_AT, List.of("DDD"), null, "Updated body");
        Instant changedAt = CREATED_AT.plusSeconds(60);

        PostChange change = previous.update(replacement, previous.version(), changedAt);
        Post current = change.current();

        assertAll(
                () -> assertSame(previous, change.previous()),
                () -> assertEquals(PostRevisionEventType.UPDATE, change.eventType()),
                () -> assertEquals(1, change.expectedRevision()),
                () -> assertEquals("post-id:1", change.expectedVersion()),
                () -> assertEquals("Title", previous.title()),
                () -> assertEquals("Updated", current.title()),
                () -> assertEquals(previous.id(), current.id()),
                () -> assertEquals(previous.slugValue(), current.slugValue()),
                () -> assertEquals(previous.author(), current.author()),
                () -> assertEquals(PostStatus.DRAFT, current.status()),
                () -> assertEquals(2, current.revision()),
                () -> assertEquals("post-id:2", current.version()),
                () -> assertEquals(changedAt, current.updatedAt()),
                () -> assertNotEquals(previous, current));
    }

    @Test
    void everyPublishIncrementsRevisionEvenWhenAlreadyPublished() {
        Post draft = draft();
        PostChange first = draft.publish(draft.version(), CREATED_AT.plusSeconds(1));
        PostChange second = first.current().publish(
                first.current().version(), CREATED_AT.plusSeconds(2));

        assertAll(
                () -> assertEquals(PostStatus.PUBLISHED, first.current().status()),
                () -> assertEquals(2, first.current().revision()),
                () -> assertEquals(PostRevisionEventType.PUBLISH, first.eventType()),
                () -> assertEquals(PostStatus.PUBLISHED, second.current().status()),
                () -> assertEquals(3, second.current().revision()),
                () -> assertEquals("post-id:3", second.current().version()),
                () -> assertEquals(PostRevisionEventType.PUBLISH, second.eventType()));
    }

    @Test
    void everyUnpublishIncrementsRevisionEvenWhenAlreadyDraft() {
        Post draft = draft();
        PostChange first = draft.unpublish(draft.version(), CREATED_AT.plusSeconds(1));
        PostChange second = first.current().unpublish(
                first.current().version(), CREATED_AT.plusSeconds(2));

        assertAll(
                () -> assertEquals(PostStatus.DRAFT, first.current().status()),
                () -> assertEquals(2, first.current().revision()),
                () -> assertEquals(PostRevisionEventType.UNPUBLISH, first.eventType()),
                () -> assertEquals(PostStatus.DRAFT, second.current().status()),
                () -> assertEquals(3, second.current().revision()),
                () -> assertEquals(PostRevisionEventType.UNPUBLISH, second.eventType()));
    }

    @Test
    void archivesAPostAndRejectsFurtherChanges() {
        Post published = draft().publish("post-id:1", CREATED_AT.plusSeconds(1)).current();

        PostChange archived = published.archive(published.version(), CREATED_AT.plusSeconds(2));

        assertAll(
                () -> assertEquals(PostRevisionEventType.ARCHIVE, archived.eventType()),
                () -> assertTrue(archived.current().archived()),
                () -> assertEquals(PostStatus.PUBLISHED, archived.current().status()),
                () -> assertEquals(3, archived.current().revision()));

        DomainException exception = assertThrows(DomainException.class,
                () -> archived.current().update(content("Again"), archived.current().version(),
                        CREATED_AT.plusSeconds(3)));
        assertEquals(DomainErrorCode.INVALID_STATE, exception.code());
    }

    @Test
    void rejectsStaleOrMalformedVersionsForEveryMutation() {
        Post post = draft();

        assertVersionConflict(() -> post.update(content("Updated"), "post-id:0", CREATED_AT.plusSeconds(1)));
        assertVersionConflict(() -> post.publish("other-id:1", CREATED_AT.plusSeconds(1)));
        assertVersionConflict(() -> post.unpublish("bad-token", CREATED_AT.plusSeconds(1)));
        assertVersionConflict(() -> post.archive(null, CREATED_AT.plusSeconds(1)));
    }

    @Test
    void rejectsOperationTimeBeforeCurrentRevision() {
        Post post = draft();

        DomainException exception = assertThrows(DomainException.class,
                () -> post.publish(post.version(), CREATED_AT.minusSeconds(1)));
        assertEquals(DomainErrorCode.INVALID_STATE, exception.code());
    }

    @Test
    void exposesRevisionEventNamesThatMatchPersistenceValues() {
        assertEquals(
                List.of("CREATE", "IMPORT", "UPDATE", "PUBLISH", "UNPUBLISH", "ARCHIVE"),
                java.util.Arrays.stream(PostRevisionEventType.values()).map(Enum::name).toList());
    }

    private static Post draft() {
        return Post.createDraft("post-id", "ddd-post", content("Title"), AUTHOR, CREATED_AT);
    }

    private static PostContent content(String title) {
        return new PostContent(title, "Description", CREATED_AT, List.of("Java"), null, "Body");
    }

    private static void assertVersionConflict(org.junit.jupiter.api.function.Executable executable) {
        DomainException exception = assertThrows(DomainException.class, executable);
        assertEquals(DomainErrorCode.VERSION_CONFLICT, exception.code());
    }
}
