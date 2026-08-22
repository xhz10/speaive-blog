package com.speaive.blog.application;

import com.speaive.blog.domain.ArchivedPost;
import com.speaive.blog.domain.MediaContent;
import com.speaive.blog.domain.Post;
import com.speaive.blog.domain.PostCollection;
import com.speaive.blog.domain.PostStatus;
import com.speaive.blog.domain.StoredMedia;

public final class BlogApplicationService {
    private final ContentStorePort contentStore;

    public BlogApplicationService(ContentStorePort contentStore) {
        this.contentStore = contentStore;
    }

    public PostCollection listStudioPosts() {
        return contentStore.scan(true);
    }

    public PostCollection listPublishedPosts() {
        return contentStore.scan(false);
    }

    public Post getStudioPost(String slug) {
        return contentStore.find(slug, true)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在"));
    }

    public Post getPublishedPost(String slug) {
        return contentStore.find(slug, false)
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "文章不存在"));
    }

    public Post createDraft(PostWriteCommand command) {
        return contentStore.createDraft(command);
    }

    public Post update(String slug, String version, PostWriteCommand command) {
        return contentStore.update(slug, version, command);
    }

    public Post publish(String slug, String version) {
        return contentStore.transition(slug, PostStatus.PUBLISHED, version);
    }

    public Post unpublish(String slug, String version) {
        return contentStore.transition(slug, PostStatus.DRAFT, version);
    }

    public ArchivedPost archive(String slug, String version) {
        return contentStore.archive(slug, version);
    }

    public Post importDraft(String fileName, byte[] markdown) {
        return contentStore.importDraft(fileName, markdown);
    }

    public StoredMedia storeMedia(String fileName, String mimeType, byte[] bytes) {
        return contentStore.storeMedia(fileName, mimeType, bytes);
    }

    public MediaContent readMedia(String path) {
        return contentStore.readMedia(path);
    }

    public MediaContent readPublicMedia(String path) {
        if (!contentStore.isMediaPublic(path)) {
            throw new BlogException(BlogErrorCode.NOT_FOUND, "图片不存在");
        }
        return contentStore.readMedia(path);
    }

    public String preview(String markdown) {
        return contentStore.renderMarkdown(markdown);
    }
}
