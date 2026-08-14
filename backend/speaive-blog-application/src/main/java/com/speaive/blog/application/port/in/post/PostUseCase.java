package com.speaive.blog.application.port.in.post;

import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.result.post.ArchivedPostResult;
import com.speaive.blog.application.result.post.PostDetailResult;
import com.speaive.blog.application.result.post.PostListResult;

public interface PostUseCase {
    PostListResult listStudioPosts();

    PostListResult listPublishedPosts();

    PostDetailResult getStudioPost(String slug);

    PostDetailResult getPublishedPost(String slug);

    PostDetailResult createDraft(PostWriteCommand command);

    PostDetailResult update(String slug, String version, PostWriteCommand command);

    PostDetailResult publish(String slug, String version);

    PostDetailResult unpublish(String slug, String version);

    ArchivedPostResult archive(String slug, String version);
}
