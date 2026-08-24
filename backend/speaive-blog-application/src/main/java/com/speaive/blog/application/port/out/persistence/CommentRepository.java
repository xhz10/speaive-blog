package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.comment.Comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    List<Comment> findByPostId(String postId, CommentQueryScope scope);

    Optional<Comment> findById(String id);

    void add(Comment comment);

    void save(Comment previous, Comment current);
}
