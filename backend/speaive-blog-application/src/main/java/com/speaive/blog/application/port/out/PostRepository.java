package com.speaive.blog.application.port.out;

import com.speaive.blog.domain.ArchivedPost;
import com.speaive.blog.domain.Post;
import com.speaive.blog.domain.PostChange;
import com.speaive.blog.domain.PostRevisionEventType;
import com.speaive.blog.domain.PostSummary;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    List<PostSummary> findAll(PostQueryScope scope);

    Optional<Post> findBySlug(String slug, PostQueryScope scope);

    Optional<Post> lockBySlug(String slug);

    void add(Post post, PostRevisionEventType eventType);

    void save(PostChange change);

    ArchivedPost archive(PostChange change);
}
