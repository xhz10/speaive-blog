package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogPostPo;
import com.speaive.blog.infrastructure.content.persistence.po.PostStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.RevisionEventTypePo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface BlogPostDatabaseMapper extends BaseMapper<BlogPostPo> {
    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.body, p.cover, p.author_id AS "authorId", p.revision, p.created_at
            FROM blog_post p
            WHERE p.slug = #{slug}
            """)
    BlogPostPo selectBySlug(@Param("slug") String slug);

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.body, p.cover, p.author_id AS "authorId", p.revision, p.created_at
            FROM blog_post p
            WHERE p.slug = #{slug} AND p.status = #{status}
            """)
    BlogPostPo selectBySlugAndStatus(@Param("slug") String slug, @Param("status") PostStatusPo status);

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.cover, p.author_id AS "authorId", p.revision, p.created_at
            FROM blog_post p
            ORDER BY p.published_at DESC, p.slug
            """)
    List<BlogPostPo> selectAllSummaries();

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.cover, p.author_id AS "authorId", p.revision, p.created_at
            FROM blog_post p
            WHERE p.status = #{status}
            ORDER BY p.published_at DESC, p.slug
            """)
    List<BlogPostPo> selectSummariesByStatus(@Param("status") PostStatusPo status);

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.body, p.cover, p.author_id AS "authorId", p.revision, p.created_at
            FROM blog_post p
            WHERE p.slug = #{slug}
            FOR UPDATE OF p
            """)
    BlogPostPo lockBySlug(@Param("slug") String slug);

    @Update("""
            UPDATE blog_post
            SET title = #{post.title},
                description = #{post.description},
                published_at = #{post.publishedAt},
                updated_at = #{post.updatedAt},
                status = #{post.status},
                body = #{post.body},
                cover = #{post.cover},
                revision = #{post.revision}
            WHERE id = #{post.id} AND slug = #{post.slug} AND revision = #{expectedRevision}
            """)
    int updateCas(@Param("post") BlogPostPo post, @Param("expectedRevision") long expectedRevision);

    @Delete("DELETE FROM blog_post WHERE id = #{postId} AND slug = #{slug} AND revision = #{revision}")
    int deleteCas(
            @Param("postId") String postId,
            @Param("slug") String slug,
            @Param("revision") long revision);

    @Select("SELECT tag FROM blog_post_tag WHERE post_id = #{postId} ORDER BY tag_order")
    List<String> selectTags(@Param("postId") String postId);

    @Delete("DELETE FROM blog_post_tag WHERE post_id = #{postId}")
    int deleteTags(@Param("postId") String postId);

    @Insert("""
            <script>
            INSERT INTO blog_post_tag (post_id, tag_order, tag) VALUES
            <foreach collection="tags" item="tag" index="index" separator=",">
                (#{postId}, #{index}, #{tag})
            </foreach>
            </script>
            """)
    int insertTags(@Param("postId") String postId, @Param("tags") List<String> tags);

    @Insert("""
            INSERT INTO blog_post_revision (
                post_id, revision, slug, title, description, published_at, updated_at,
                status, body, cover, author_id, post_created_at, event_type, recorded_at
            )
            SELECT id, revision, slug, title, description, published_at, updated_at,
                   status, body, cover, author_id, created_at, #{eventType}, #{recordedAt}
            FROM blog_post
            WHERE id = #{postId}
            """)
    int insertRevisionSnapshot(
            @Param("postId") String postId,
            @Param("eventType") RevisionEventTypePo eventType,
            @Param("recordedAt") Instant recordedAt);

    @Insert("""
            INSERT INTO blog_post_revision_tag (post_id, revision, tag_order, tag)
            SELECT post_id, #{revision}, tag_order, tag
            FROM blog_post_tag
            WHERE post_id = #{postId}
            """)
    int insertRevisionTags(@Param("postId") String postId, @Param("revision") long revision);
}
