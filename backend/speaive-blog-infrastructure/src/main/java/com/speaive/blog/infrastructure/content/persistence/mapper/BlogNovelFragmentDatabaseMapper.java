package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogNovelFragmentPo;
import com.speaive.blog.infrastructure.content.persistence.po.NovelFragmentRevisionEventTypePo;
import com.speaive.blog.infrastructure.content.persistence.po.NovelFragmentStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.NovelFragmentVisibilityPo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface BlogNovelFragmentDatabaseMapper extends BaseMapper<BlogNovelFragmentPo> {
    String COLUMNS = """
            id, slug, title, excerpt, body, author_id AS \"authorId\", status, visibility,
            published_at, revision, created_at, updated_at
            """;

    @Select("SELECT " + COLUMNS + " FROM blog_novel_fragment WHERE slug = #{slug}")
    BlogNovelFragmentPo selectBySlug(@Param("slug") String slug);

    @Select("""
            SELECT id, slug, title, excerpt, body, author_id AS "authorId", status, visibility,
                   published_at, revision, created_at, updated_at
            FROM blog_novel_fragment
            WHERE slug = #{slug} AND status = #{status} AND visibility = #{visibility}
            """)
    BlogNovelFragmentPo selectBySlugAndStatusAndVisibility(
            @Param("slug") String slug,
            @Param("status") NovelFragmentStatusPo status,
            @Param("visibility") NovelFragmentVisibilityPo visibility);

    @Select("""
            SELECT id, slug, title, excerpt, body, author_id AS "authorId", status, visibility,
                   published_at, revision, created_at, updated_at
            FROM blog_novel_fragment
            ORDER BY updated_at DESC, slug
            """)
    List<BlogNovelFragmentPo> selectAllFragments();

    @Select("""
            SELECT id, slug, title, excerpt, body, author_id AS "authorId", status, visibility,
                   published_at, revision, created_at, updated_at
            FROM blog_novel_fragment
            WHERE status = #{status} AND visibility = #{visibility}
            ORDER BY published_at DESC, slug
            """)
    List<BlogNovelFragmentPo> selectPublishedFragments(
            @Param("status") NovelFragmentStatusPo status,
            @Param("visibility") NovelFragmentVisibilityPo visibility);

    @Select("""
            SELECT id, slug, title, excerpt, body, author_id AS "authorId", status, visibility,
                   published_at, revision, created_at, updated_at
            FROM blog_novel_fragment
            WHERE slug = #{slug}
            FOR UPDATE
            """)
    BlogNovelFragmentPo lockBySlug(@Param("slug") String slug);

    @Update("""
            UPDATE blog_novel_fragment
            SET title = #{fragment.title},
                excerpt = #{fragment.excerpt},
                body = #{fragment.body},
                status = #{fragment.status},
                visibility = #{fragment.visibility},
                published_at = #{fragment.publishedAt},
                revision = #{fragment.revision},
                updated_at = #{fragment.updatedAt}
            WHERE id = #{fragment.id} AND slug = #{fragment.slug} AND revision = #{expectedRevision}
            """)
    int updateCas(
            @Param("fragment") BlogNovelFragmentPo fragment,
            @Param("expectedRevision") long expectedRevision);

    @Insert("""
            INSERT INTO blog_novel_fragment_revision (
                fragment_id, revision, slug, title, excerpt, body, author_id, status, visibility,
                published_at, fragment_created_at, updated_at, event_type, recorded_at
            )
            SELECT id, revision, slug, title, excerpt, body, author_id, status, visibility,
                   published_at, created_at, updated_at, #{eventType}, #{recordedAt}
            FROM blog_novel_fragment
            WHERE id = #{fragmentId}
            """)
    int insertRevisionSnapshot(
            @Param("fragmentId") String fragmentId,
            @Param("eventType") NovelFragmentRevisionEventTypePo eventType,
            @Param("recordedAt") Instant recordedAt);
}
