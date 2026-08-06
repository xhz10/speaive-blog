package com.speaive.blog.infrastructure.content;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface BlogPersistenceMapper extends BaseMapper<BlogPostEntity> {

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.body, p.cover, p.author_id AS "authorId", p.revision, p.created_at,
                   u.username AS "authorUsername", u.display_name AS "authorDisplayName",
                   u.type AS "authorType", u.avatar_url AS "authorAvatarUrl"
            FROM blog_post p
            JOIN blog_user u ON u.id = p.author_id
            WHERE p.slug = #{slug}
            """)
    BlogPostEntity selectBySlug(@Param("slug") String slug);

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.body, p.cover, p.author_id AS "authorId", p.revision, p.created_at,
                   u.username AS "authorUsername", u.display_name AS "authorDisplayName",
                   u.type AS "authorType", u.avatar_url AS "authorAvatarUrl"
            FROM blog_post p
            JOIN blog_user u ON u.id = p.author_id
            WHERE p.slug = #{slug} AND p.status = 'PUBLISHED'
            """)
    BlogPostEntity selectPublishedBySlug(@Param("slug") String slug);

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.cover, p.author_id AS "authorId", p.revision, p.created_at,
                   u.username AS "authorUsername", u.display_name AS "authorDisplayName",
                   u.type AS "authorType", u.avatar_url AS "authorAvatarUrl"
            FROM blog_post p
            JOIN blog_user u ON u.id = p.author_id
            ORDER BY p.published_at DESC, p.slug
            """)
    List<BlogPostEntity> selectAllSummaries();

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.cover, p.author_id AS "authorId", p.revision, p.created_at,
                   u.username AS "authorUsername", u.display_name AS "authorDisplayName",
                   u.type AS "authorType", u.avatar_url AS "authorAvatarUrl"
            FROM blog_post p
            JOIN blog_user u ON u.id = p.author_id
            WHERE p.status = 'PUBLISHED'
            ORDER BY p.published_at DESC, p.slug
            """)
    List<BlogPostEntity> selectPublishedSummaries();

    @Select("""
            SELECT p.id, p.slug, p.title, p.description, p.published_at, p.updated_at, p.status,
                   p.body, p.cover, p.author_id AS "authorId", p.revision, p.created_at,
                   u.username AS "authorUsername", u.display_name AS "authorDisplayName",
                   u.type AS "authorType", u.avatar_url AS "authorAvatarUrl"
            FROM blog_post p
            JOIN blog_user u ON u.id = p.author_id
            WHERE p.slug = #{slug}
            FOR UPDATE OF p
            """)
    BlogPostEntity lockBySlug(@Param("slug") String slug);

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
    int updateCas(@Param("post") BlogPostEntity post, @Param("expectedRevision") long expectedRevision);

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
            @Param("eventType") String eventType,
            @Param("recordedAt") Instant recordedAt);

    @Insert("""
            INSERT INTO blog_post_revision_tag (post_id, revision, tag_order, tag)
            SELECT post_id, #{revision}, tag_order, tag
            FROM blog_post_tag
            WHERE post_id = #{postId}
            """)
    int insertRevisionTags(@Param("postId") String postId, @Param("revision") long revision);

    @Insert("""
            INSERT INTO blog_media (
                relative_path, original_file_name, mime_type, size_bytes, sha256, created_at
            ) VALUES (
                #{media.relativePath}, #{media.originalFileName}, #{media.mimeType},
                #{media.sizeBytes}, #{media.sha256}, #{media.createdAt}
            )
            """)
    int insertMedia(@Param("media") BlogMediaEntity media);

    @Select("""
            SELECT relative_path, original_file_name, mime_type, size_bytes, sha256, created_at
            FROM blog_media
            WHERE relative_path = #{relativePath}
            """)
    BlogMediaEntity selectMedia(@Param("relativePath") String relativePath);

    @Select("SELECT COUNT(*) FROM blog_markdown_import WHERE sha256 = #{sha256}")
    int countImport(@Param("sha256") String sha256);

    @Insert("""
            INSERT INTO blog_markdown_import (sha256, original_file_name, slug, imported_at)
            VALUES (#{sha256}, #{fileName}, #{slug}, #{importedAt})
            """)
    int insertImport(
            @Param("sha256") String sha256,
            @Param("fileName") String fileName,
            @Param("slug") String slug,
            @Param("importedAt") Instant importedAt);
}
