package com.speaive.blog.application;

import com.speaive.blog.domain.ArchivedPost;
import com.speaive.blog.domain.MediaContent;
import com.speaive.blog.domain.Post;
import com.speaive.blog.domain.PostCollection;
import com.speaive.blog.domain.PostStatus;
import com.speaive.blog.domain.StoredMedia;

import java.util.Optional;

public interface ContentStorePort {
    PostCollection scan(boolean includeDrafts);

    Optional<Post> find(String slug, boolean includeDrafts);

    Post createDraft(PostWriteCommand command);

    Post update(String slug, String expectedVersion, PostWriteCommand command);

    Post transition(String slug, PostStatus targetStatus, String expectedVersion);

    ArchivedPost archive(String slug, String expectedVersion);

    Post importDraft(String fileName, byte[] markdown);

    StoredMedia storeMedia(String fileName, String declaredMimeType, byte[] bytes);

    MediaContent readMedia(String relativePath);

    boolean isMediaPublic(String relativePath);

    String renderMarkdown(String markdown);
}
