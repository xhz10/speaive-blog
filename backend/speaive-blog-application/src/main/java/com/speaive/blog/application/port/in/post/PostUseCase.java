package com.speaive.blog.application.port.in.post;

import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.result.post.ArchivedPostResult;
import com.speaive.blog.application.result.post.PostDetailResult;
import com.speaive.blog.application.result.post.PostListResult;

/**
 * 文章入站用例契约，供公开阅读和站长 HTTP 入口调用；参数与返回值属于应用边界，不能暴露领域聚合或持久化对象。
 */
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
