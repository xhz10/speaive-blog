package com.speaive.blog.domain.novel;

public record NovelFragmentChange(
        NovelFragment previous,
        NovelFragment current,
        NovelFragmentRevisionEventType eventType
) {
    public long expectedRevision() {
        return previous.revision();
    }
}
