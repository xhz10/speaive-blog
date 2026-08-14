package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostChange;
import com.speaive.blog.domain.post.PostRevisionEventType;
import com.speaive.blog.domain.post.PostSummary;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    List<PostSummary> findAll(PostQueryScope scope);

    Optional<Post> findBySlug(String slug, PostQueryScope scope);

    Optional<Post> lockBySlug(String slug);

    void add(Post post, PostRevisionEventType eventType);

    void save(PostChange change);

    ArchiveReceipt archive(PostChange change);
}
