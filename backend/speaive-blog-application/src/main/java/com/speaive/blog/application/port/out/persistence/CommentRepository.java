package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.comment.Comment;

import java.util.List;
import java.util.Optional;

/**
 * 评论持久化端口。读取范围只过滤评论状态；文章可见性由用例先校验，状态保存必须比较变更前状态以检测竞争。
 */
public interface CommentRepository {
    List<Comment> findByPostId(String postId, CommentQueryScope scope);

    Optional<Comment> findById(String id);

    void add(Comment comment);

    void save(Comment previous, Comment current);
}
