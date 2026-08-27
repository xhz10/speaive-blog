package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.post.Post;
import com.speaive.blog.domain.post.PostChange;
import com.speaive.blog.domain.post.PostRevisionEventType;
import com.speaive.blog.domain.post.PostSummary;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PostRepository {
    List<PostSummary> findAll(PostQueryScope scope);

    Optional<Post> findBySlug(String slug, PostQueryScope scope);

    Optional<Post> findById(String postId, PostQueryScope scope);

    Optional<Post> lockBySlug(String slug);

    void add(Post post, PostRevisionEventType eventType, Set<String> mediaPaths);

    void save(PostChange change, Set<String> mediaPaths);

    ArchiveReceipt archive(PostChange change);
}
