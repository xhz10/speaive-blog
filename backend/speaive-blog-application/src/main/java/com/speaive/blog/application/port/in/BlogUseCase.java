package com.speaive.blog.application.port.in;

import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.application.result.ArchivedPostResult;
import com.speaive.blog.application.result.MediaContentResult;
import com.speaive.blog.application.result.PostDetailResult;
import com.speaive.blog.application.result.PostListResult;
import com.speaive.blog.application.result.StoredMediaResult;

public interface BlogUseCase {
    PostListResult listStudioPosts();

    PostListResult listPublishedPosts();

    PostDetailResult getStudioPost(String slug);

    PostDetailResult getPublishedPost(String slug);

    PostDetailResult createDraft(PostWriteCommand command);

    PostDetailResult update(String slug, String version, PostWriteCommand command);

    PostDetailResult publish(String slug, String version);

    PostDetailResult unpublish(String slug, String version);

    ArchivedPostResult archive(String slug, String version);

    PostDetailResult importDraft(String fileName, byte[] markdown);

    StoredMediaResult storeMedia(String fileName, String mimeType, byte[] bytes);

    MediaContentResult readMedia(String path);

    String preview(String markdown);
}
