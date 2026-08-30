package com.speaive.blog.domain.novel;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.Objects;

public final class NovelFragment {
    private final NovelFragmentSnapshot snapshot;

    private NovelFragment(NovelFragmentSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public static NovelFragment createDraft(
            String id,
            NovelFragmentSlug slug,
            NovelFragmentContent content,
            Author author,
            NovelFragmentVisibility visibility,
            Instant now) {
        requireAuthor(author).ensureCanAuthor();
        Instant createdAt = requireTime(now);
        return new NovelFragment(new NovelFragmentSnapshot(
                id,
                Objects.requireNonNull(slug, "slug"),
                requireContent(content),
                author,
                NovelFragmentStatus.DRAFT,
                requireVisibility(visibility),
                null,
                createdAt,
                createdAt,
                1
        ));
    }

    public static NovelFragment createDraft(
            String id,
            NovelFragmentSlug slug,
            NovelFragmentContent content,
            Author author,
            Instant now) {
        return createDraft(id, slug, content, author, NovelFragmentVisibility.ADMIN_ONLY, now);
    }

    public static NovelFragment rehydrate(NovelFragmentSnapshot snapshot) {
        return new NovelFragment(snapshot);
    }

    public NovelFragmentChange update(
            NovelFragmentContent content,
            NovelFragmentVisibility visibility,
            String expectedVersion,
            Instant now) {
        assertVersion(expectedVersion);
        return change(
                requireContent(content),
                status(),
                requireVisibility(visibility),
                publishedAt(),
                requireNextTime(now),
                NovelFragmentRevisionEventType.UPDATE
        );
    }

    public NovelFragmentChange publish(String expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        Instant changedAt = requireNextTime(now);
        return change(
                content(),
                NovelFragmentStatus.PUBLISHED,
                visibility(),
                publishedAt() == null ? changedAt : publishedAt(),
                changedAt,
                NovelFragmentRevisionEventType.PUBLISH
        );
    }

    public NovelFragmentChange unpublish(String expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        return change(
                content(),
                NovelFragmentStatus.DRAFT,
                visibility(),
                publishedAt(),
                requireNextTime(now),
                NovelFragmentRevisionEventType.UNPUBLISH
        );
    }

    public void assertVersion(String expectedVersion) {
        if (!version().equals(expectedVersion)) {
            throw new DomainException(DomainErrorCode.VERSION_CONFLICT,
                    "小说片段已被其他操作更新，请刷新后重试");
        }
    }

    private NovelFragmentChange change(
            NovelFragmentContent nextContent,
            NovelFragmentStatus nextStatus,
            NovelFragmentVisibility nextVisibility,
            Instant nextPublishedAt,
            Instant now,
            NovelFragmentRevisionEventType eventType) {
        NovelFragment current = new NovelFragment(new NovelFragmentSnapshot(
                id(), slugValue(), nextContent, author(), nextStatus, nextVisibility, nextPublishedAt,
                createdAt(), now, revision() + 1
        ));
        return new NovelFragmentChange(this, current, eventType);
    }

    private Instant requireNextTime(Instant value) {
        Instant time = requireTime(value);
        if (time.isBefore(updatedAt())) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "小说片段更新时间不能早于当前版本");
        }
        return time;
    }

    private static Instant requireTime(Instant value) {
        if (value == null) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "小说片段操作时间不能为空");
        }
        return value;
    }

    private static Author requireAuthor(Author author) {
        if (author == null) {
            throw new DomainException(DomainErrorCode.INVALID_AUTHOR, "小说片段作者不能为空");
        }
        return author;
    }

    private static NovelFragmentContent requireContent(NovelFragmentContent content) {
        if (content == null) {
            throw new DomainException(DomainErrorCode.INVALID_CONTENT, "小说片段内容不能为空");
        }
        return content;
    }

    private static NovelFragmentVisibility requireVisibility(NovelFragmentVisibility visibility) {
        if (visibility == null) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "小说片段可见性不能为空");
        }
        return visibility;
    }

    public String id() { return snapshot.id(); }
    public String slug() { return snapshot.slug().value(); }
    public NovelFragmentSlug slugValue() { return snapshot.slug(); }
    public NovelFragmentContent content() { return snapshot.content(); }
    public String title() { return content().title(); }
    public String excerpt() { return content().excerpt(); }
    public String body() { return content().body(); }
    public Author author() { return snapshot.author(); }
    public NovelFragmentStatus status() { return snapshot.status(); }
    public NovelFragmentVisibility visibility() { return snapshot.visibility(); }
    public Instant publishedAt() { return snapshot.publishedAt(); }
    public Instant createdAt() { return snapshot.createdAt(); }
    public Instant updatedAt() { return snapshot.updatedAt(); }
    public long revision() { return snapshot.revision(); }
    public String version() { return snapshot.version(); }
    public NovelFragmentSnapshot snapshot() { return snapshot; }
}
