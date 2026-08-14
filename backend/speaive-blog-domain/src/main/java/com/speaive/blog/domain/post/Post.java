package com.speaive.blog.domain.post;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class Post {
    private final PostSnapshot snapshot;

    private Post(PostSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public static Post createDraft(
            String id,
            PostSlug slug,
            PostContent content,
            Author author,
            Instant now) {
        requireAuthor(author).ensureCanAuthor();
        requireContent(content);
        Instant createdAt = requireTime(now);
        return new Post(new PostSnapshot(id, slug, content, author, PostStatus.DRAFT,
                createdAt, createdAt, 1, false));
    }

    public static Post createDraft(
            String id,
            String slug,
            PostContent content,
            Author author,
            Instant now) {
        return createDraft(id, PostSlug.of(slug), content, author, now);
    }

    public static Post rehydrate(PostSnapshot snapshot) {
        return new Post(snapshot);
    }

    public PostChange update(PostContent content, String expectedVersion, Instant now) {
        assertMutable(expectedVersion);
        return change(requireContent(content), status(), false,
                requireNextTime(now), PostRevisionEventType.UPDATE);
    }

    public PostChange publish(String expectedVersion, Instant now) {
        assertMutable(expectedVersion);
        return change(content(), PostStatus.PUBLISHED, false,
                requireNextTime(now), PostRevisionEventType.PUBLISH);
    }

    public PostChange unpublish(String expectedVersion, Instant now) {
        assertMutable(expectedVersion);
        return change(content(), PostStatus.DRAFT, false,
                requireNextTime(now), PostRevisionEventType.UNPUBLISH);
    }

    public PostChange archive(String expectedVersion, Instant now) {
        assertMutable(expectedVersion);
        return change(content(), status(), true,
                requireNextTime(now), PostRevisionEventType.ARCHIVE);
    }

    public void assertVersion(String expectedVersion) {
        if (!version().equals(expectedVersion)) {
            throw new DomainException(DomainErrorCode.VERSION_CONFLICT,
                    "文章已被其他操作更新，请刷新后重试");
        }
    }

    private PostChange change(
            PostContent nextContent,
            PostStatus nextStatus,
            boolean nextArchived,
            Instant now,
            PostRevisionEventType eventType) {
        Post current = new Post(new PostSnapshot(id(), slugValue(), nextContent, author(), nextStatus,
                createdAt(), now, revision() + 1, nextArchived));
        return new PostChange(this, current, eventType);
    }

    private void assertMutable(String expectedVersion) {
        if (archived()) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "已归档文章不能再修改");
        }
        assertVersion(expectedVersion);
    }

    private Instant requireNextTime(Instant value) {
        Instant time = requireTime(value);
        if (time.isBefore(updatedAt())) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "文章更新时间不能早于当前版本");
        }
        return time;
    }

    private static Instant requireTime(Instant value) {
        if (value == null) {
            throw new DomainException(DomainErrorCode.INVALID_STATE, "文章操作时间不能为空");
        }
        return value;
    }

    private static Author requireAuthor(Author author) {
        if (author == null) {
            throw new DomainException(DomainErrorCode.INVALID_AUTHOR, "文章作者不能为空");
        }
        return author;
    }

    private static PostContent requireContent(PostContent content) {
        if (content == null) {
            throw new DomainException(DomainErrorCode.INVALID_CONTENT, "文章内容不能为空");
        }
        return content;
    }

    public String id() {
        return snapshot.id();
    }

    public String slug() {
        return snapshot.slug().value();
    }

    public PostSlug slugValue() {
        return snapshot.slug();
    }

    public PostContent content() {
        return snapshot.content();
    }

    public String title() {
        return content().title();
    }

    public String description() {
        return content().description();
    }

    public Instant publishedAt() {
        return content().publishedAt();
    }

    public List<String> tags() {
        return content().tags();
    }

    public String cover() {
        return content().cover();
    }

    public String body() {
        return content().body();
    }

    public Author author() {
        return snapshot.author();
    }

    public PostStatus status() {
        return snapshot.status();
    }

    public Instant createdAt() {
        return snapshot.createdAt();
    }

    public Instant updatedAt() {
        return snapshot.updatedAt();
    }

    public long revision() {
        return snapshot.revision();
    }

    public boolean archived() {
        return snapshot.archived();
    }

    public String version() {
        return snapshot.version();
    }

    public PostSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Post post && snapshot.equals(post.snapshot);
    }

    @Override
    public int hashCode() {
        return snapshot.hashCode();
    }

    @Override
    public String toString() {
        return "Post[" + version() + ", slug=" + slug() + ", status=" + status() + "]";
    }
}
