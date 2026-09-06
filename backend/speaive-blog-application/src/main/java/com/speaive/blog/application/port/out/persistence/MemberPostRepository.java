package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostChange;
import java.util.List;
import java.util.Optional;

/**
 * 会员文章存储端口，所有操作都显式限定所有者；生命周期复用 Post 聚合。
 * 与站长的 AI、分享、统计派生表分开存储，避免加密内容在既有流程留下明文副本。
 */
public interface MemberPostRepository {
    List<Post> list(String ownerId, boolean publishedOnly, int page, int pageSize);
    long count(String ownerId, boolean publishedOnly);
    Optional<Post> find(String ownerId, String slug, boolean publishedOnly);
    void add(Post post, boolean encrypted);
    void save(PostChange change, boolean encrypted);
    List<Post> revisions(String ownerId, String postId);
    Optional<Post> revision(String ownerId, String postId, long revision);
    /** 在账号行锁保护下，原子转换当前文章与所有历史版本的存储模式。 */
    void changeProtection(String ownerId, boolean encrypted);
}
