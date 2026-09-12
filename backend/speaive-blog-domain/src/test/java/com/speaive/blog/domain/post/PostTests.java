package com.speaive.blog.domain.post;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

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
    void restoresContentWithoutChangingPublicationState() {
        Post published = draft().publish("post-id:1", CREATED_AT.plusSeconds(1)).current();
        PostContent oldContent = new PostContent(
                "Old title", "Old description", CREATED_AT.minusSeconds(100), List.of("Old"), null, "Old body");

        PostChange restored = published.restore(
                oldContent, PostVisibility.ADMIN_ONLY, published.version(), CREATED_AT.plusSeconds(2));

        assertAll(
                () -> assertEquals(PostRevisionEventType.RESTORE, restored.eventType()),
                () -> assertEquals(PostStatus.PUBLISHED, restored.current().status()),
                () -> assertEquals(PostVisibility.ADMIN_ONLY, restored.current().visibility()),
                () -> assertEquals("Old title", restored.current().title()),
                () -> assertEquals(3, restored.current().revision()));
    }

    @Test
    void exposesRevisionEventNamesThatMatchPersistenceValues() {
        assertEquals(
                List.of("CREATE", "IMPORT", "UPDATE", "PUBLISH", "UNPUBLISH", "ARCHIVE", "RESTORE"),
                java.util.Arrays.stream(PostRevisionEventType.values()).map(Enum::name).toList());
    }

    @Test
    void recoversArchivedContentOnlyAsPrivateDraftWithFreshVersion() {
        Post published = draft().update(content("Title"), PostVisibility.PUBLIC, "post-id:1", CREATED_AT)
                .current().publish("post-id:2", CREATED_AT).current();
        Post archived = published.archive(published.version(), CREATED_AT).current();
        PostChange recovered = archived.recoverArchive(archived.version(), CREATED_AT.plusSeconds(1));
        assertEquals(PostStatus.DRAFT, recovered.current().status());
        assertEquals(PostVisibility.ADMIN_ONLY, recovered.current().visibility());
        assertEquals(archived.content(), recovered.current().content());
        assertEquals(archived.revision() + 1, recovered.current().revision());
        assertFalse(recovered.current().archived());
        assertVersionConflict(() -> archived.recoverArchive(published.version(), CREATED_AT));
        assertThrows(DomainException.class, () -> published.recoverArchive(published.version(), CREATED_AT));
        Post unsafe = Post.rehydrate(new PostSnapshot(archived.id(), archived.slugValue(), archived.content(),
                archived.author(), PostStatus.PUBLISHED, PostVisibility.PUBLIC, archived.createdAt(), CREATED_AT,
                archived.revision() + 1, false));
        assertThrows(DomainException.class, () -> new PostChange(archived, unsafe, PostRevisionEventType.RESTORE));
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
