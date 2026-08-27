package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogCommentPo;
import com.speaive.blog.infrastructure.content.persistence.po.CommentStatusPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BlogCommentDatabaseMapper extends BaseMapper<BlogCommentPo> {
    @Select("""
            SELECT id, post_id AS "postId", parent_comment_id AS "parentCommentId",
                   author_id AS "authorId", body, status,
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_comment
            WHERE post_id = #{postId}
            ORDER BY created_at, id
            """)
    List<BlogCommentPo> selectAllByPostId(@Param("postId") String postId);

    @Select("""
            SELECT id, post_id AS "postId", parent_comment_id AS "parentCommentId",
                   author_id AS "authorId", body, status,
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_comment
            WHERE post_id = #{postId} AND status = #{status}
            ORDER BY created_at, id
            """)
    List<BlogCommentPo> selectByPostIdAndStatus(
            @Param("postId") String postId,
            @Param("status") CommentStatusPo status);

    @Select("""
            SELECT id, post_id AS "postId", parent_comment_id AS "parentCommentId",
                   author_id AS "authorId", body, status,
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_comment
            WHERE id = #{id}
            """)
    BlogCommentPo selectCommentById(@Param("id") String id);

    @Update("""
            UPDATE blog_comment
            SET status = #{current.status}, updated_at = #{current.updatedAt}
            WHERE id = #{current.id} AND status = #{expectedStatus}
            """)
    int updateStatusCas(
            @Param("current") BlogCommentPo current,
            @Param("expectedStatus") CommentStatusPo expectedStatus);
}
