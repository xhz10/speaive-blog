package com.speaive.blog.application.port.in.post;

import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.result.post.*;

/** 会员写作入口。authenticatedUsername 来自会话，任何写操作都不能由客户端选择所有者。 */
public interface MemberWritingUseCase {
    MemberPostListResult listOwn(String authenticatedUsername, int page);
    PostDetailResult getOwn(String authenticatedUsername, String slug);
    PostDetailResult create(String authenticatedUsername, PostWriteCommand command);
    PostDetailResult update(String authenticatedUsername, String slug, String version, PostWriteCommand command);
    PostDetailResult publish(String authenticatedUsername, String slug, String version);
    PostDetailResult unpublish(String authenticatedUsername, String slug, String version);
    void archive(String authenticatedUsername, String slug, String version);
    MemberPostHistoryResult history(String authenticatedUsername, String slug);
    PostDetailResult restore(String authenticatedUsername, String slug, long revision, String version);
    MemberPostListResult profile(String username, int page);
    PostDetailResult published(String username, String slug);
}
