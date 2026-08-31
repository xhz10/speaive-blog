package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.speaive.blog.infrastructure.content.persistence.po.BlogContentRevisionPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogDiscussionDigestPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogEditorialReviewPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogInspirationPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogShareGrantPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogWorkCollectionPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogWorkItemPo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface BlogCreativeDatabaseMapper {
    String INSPIRATION_COLUMNS = """
            id, title, body, kind, status, pinned, target_type AS "targetType",
            target_slug AS "targetSlug", revision, created_at AS "createdAt", updated_at AS "updatedAt"
            """;

    @Select("SELECT " + INSPIRATION_COLUMNS + " FROM blog_inspiration "
            + "ORDER BY pinned DESC, updated_at DESC, id")
    List<BlogInspirationPo> selectInspirations();

    @Select("SELECT " + INSPIRATION_COLUMNS + " FROM blog_inspiration WHERE id = #{id}")
    BlogInspirationPo selectInspiration(@Param("id") String id);

    @Insert("""
            INSERT INTO blog_inspiration (
                id, title, body, kind, status, pinned, target_type, target_slug, revision, created_at, updated_at
            ) VALUES (
                #{id}, #{title}, #{body}, #{kind}, #{status}, #{pinned}, #{targetType}, #{targetSlug},
                #{revision}, #{createdAt}, #{updatedAt}
            )
            """)
    int insertInspiration(BlogInspirationPo inspiration);

    @Update("""
            UPDATE blog_inspiration
            SET title = #{value.title}, body = #{value.body}, kind = #{value.kind}, status = #{value.status},
                pinned = #{value.pinned}, target_type = #{value.targetType}, target_slug = #{value.targetSlug},
                revision = #{value.revision}, updated_at = #{value.updatedAt}
            WHERE id = #{value.id} AND revision = #{expectedRevision}
            """)
    int updateInspiration(@Param("value") BlogInspirationPo value,
                          @Param("expectedRevision") long expectedRevision);

    String WORK_COLUMNS = """
            id, slug, title, description, cover, visibility, revision,
            created_at AS "createdAt", updated_at AS "updatedAt"
            """;

    @Select("SELECT " + WORK_COLUMNS + " FROM blog_work_collection ORDER BY updated_at DESC, slug")
    List<BlogWorkCollectionPo> selectWorks();

    @Select("SELECT " + WORK_COLUMNS + " FROM blog_work_collection WHERE slug = #{slug}")
    BlogWorkCollectionPo selectWork(@Param("slug") String slug);

    @Select("""
            SELECT collection_id AS "collectionId", item_order AS "itemOrder",
                   content_type AS "contentType", content_slug AS "contentSlug"
            FROM blog_work_collection_item
            WHERE collection_id = #{collectionId}
            ORDER BY item_order
            """)
    List<BlogWorkItemPo> selectWorkItems(@Param("collectionId") String collectionId);

    @Insert("""
            INSERT INTO blog_work_collection (
                id, slug, title, description, cover, visibility, revision, created_at, updated_at
            ) VALUES (
                #{id}, #{slug}, #{title}, #{description}, #{cover}, #{visibility}, #{revision},
                #{createdAt}, #{updatedAt}
            )
            """)
    int insertWork(BlogWorkCollectionPo work);

    @Update("""
            UPDATE blog_work_collection
            SET title = #{value.title}, description = #{value.description}, cover = #{value.cover},
                visibility = #{value.visibility}, revision = #{value.revision}, updated_at = #{value.updatedAt}
            WHERE id = #{value.id} AND slug = #{value.slug} AND revision = #{expectedRevision}
            """)
    int updateWork(@Param("value") BlogWorkCollectionPo value,
                   @Param("expectedRevision") long expectedRevision);

    @Delete("DELETE FROM blog_work_collection_item WHERE collection_id = #{collectionId}")
    int deleteWorkItems(@Param("collectionId") String collectionId);

    @Insert("""
            <script>
            INSERT INTO blog_work_collection_item (collection_id, item_order, content_type, content_slug) VALUES
            <foreach collection="items" item="item" separator=",">
                (#{item.collectionId}, #{item.itemOrder}, #{item.contentType}, #{item.contentSlug})
            </foreach>
            </script>
            """)
    int insertWorkItems(@Param("items") List<BlogWorkItemPo> items);

    String SHARE_COLUMNS = """
            id, content_type AS "contentType", content_slug AS "contentSlug", token_hash AS "tokenHash",
            expires_at AS "expiresAt", revoked_at AS "revokedAt", last_accessed_at AS "lastAccessedAt",
            created_at AS "createdAt"
            """;

    @Select("SELECT " + SHARE_COLUMNS + " FROM blog_share_grant "
            + "WHERE content_type = #{contentType} AND content_slug = #{contentSlug} ORDER BY created_at DESC")
    List<BlogShareGrantPo> selectShares(
            @Param("contentType") String contentType, @Param("contentSlug") String contentSlug);

    @Select("SELECT " + SHARE_COLUMNS + " FROM blog_share_grant WHERE id = #{id}")
    BlogShareGrantPo selectShareById(@Param("id") String id);

    @Select("SELECT " + SHARE_COLUMNS + " FROM blog_share_grant WHERE token_hash = #{tokenHash}")
    BlogShareGrantPo selectShareByTokenHash(@Param("tokenHash") String tokenHash);

    @Insert("""
            INSERT INTO blog_share_grant (
                id, content_type, content_slug, token_hash, expires_at, revoked_at, last_accessed_at, created_at
            ) VALUES (
                #{id}, #{contentType}, #{contentSlug}, #{tokenHash}, #{expiresAt}, #{revokedAt},
                #{lastAccessedAt}, #{createdAt}
            )
            """)
    int insertShare(BlogShareGrantPo share);

    @Update("UPDATE blog_share_grant SET revoked_at = #{revokedAt} WHERE id = #{id} AND revoked_at IS NULL")
    int revokeShare(@Param("id") String id, @Param("revokedAt") Instant revokedAt);

    @Update("UPDATE blog_share_grant SET last_accessed_at = #{accessedAt} WHERE id = #{id}")
    int touchShare(@Param("id") String id, @Param("accessedAt") Instant accessedAt);

    String REVIEW_COLUMNS = """
            id, post_id AS "postId", post_revision AS "postRevision", agent_id AS "agentId",
            quote_text AS "quoteText", quote_prefix AS "quotePrefix", quote_suffix AS "quoteSuffix",
            body, model, input_tokens AS "inputTokens", output_tokens AS "outputTokens", created_at AS "createdAt"
            """;

    @Select("SELECT " + REVIEW_COLUMNS + " FROM blog_editorial_review "
            + "WHERE post_id = #{postId} ORDER BY created_at DESC, id")
    List<BlogEditorialReviewPo> selectEditorialReviews(@Param("postId") String postId);

    @Insert("""
            INSERT INTO blog_editorial_review (
                id, post_id, post_revision, agent_id, quote_text, quote_prefix, quote_suffix,
                body, model, input_tokens, output_tokens, created_at
            ) VALUES (
                #{id}, #{postId}, #{postRevision}, #{agentId}, #{quoteText}, #{quotePrefix}, #{quoteSuffix},
                #{body}, #{model}, #{inputTokens}, #{outputTokens}, #{createdAt}
            )
            """)
    int insertEditorialReview(BlogEditorialReviewPo review);

    @Select("""
            SELECT post_id AS "postId", post_revision AS "postRevision",
                   comments_fingerprint AS "commentsFingerprint", body, model,
                   input_tokens AS "inputTokens", output_tokens AS "outputTokens", updated_at AS "updatedAt"
            FROM blog_discussion_digest WHERE post_id = #{postId}
            """)
    BlogDiscussionDigestPo selectDiscussionDigest(@Param("postId") String postId);

    @Insert("""
            INSERT INTO blog_discussion_digest (
                post_id, post_revision, comments_fingerprint, body, model, input_tokens, output_tokens, updated_at
            ) VALUES (
                #{postId}, #{postRevision}, #{commentsFingerprint}, #{body}, #{model},
                #{inputTokens}, #{outputTokens}, #{updatedAt}
            )
            ON CONFLICT (post_id) DO UPDATE SET
                post_revision = EXCLUDED.post_revision,
                comments_fingerprint = EXCLUDED.comments_fingerprint,
                body = EXCLUDED.body,
                model = EXCLUDED.model,
                input_tokens = EXCLUDED.input_tokens,
                output_tokens = EXCLUDED.output_tokens,
                updated_at = EXCLUDED.updated_at
            """)
    int upsertDiscussionDigest(BlogDiscussionDigestPo digest);

    @Select("""
            SELECT r.post_id AS "contentId", r.revision, r.event_type AS "eventType", r.slug, r.title,
                   r.description AS summary, r.body, r.published_at AS "publishedAt", r.cover,
                   r.status, r.visibility, r.updated_at AS "updatedAt", r.recorded_at AS "recordedAt"
            FROM blog_post_revision r
            WHERE r.post_id = (SELECT id FROM blog_post WHERE slug = #{slug})
            ORDER BY r.revision DESC
            """)
    List<BlogContentRevisionPo> selectPostRevisions(@Param("slug") String slug);

    @Select("""
            SELECT r.fragment_id AS "contentId", r.revision, r.event_type AS "eventType", r.slug, r.title,
                   r.excerpt AS summary, r.body, r.published_at AS "publishedAt", NULL AS cover,
                   r.status, r.visibility, r.updated_at AS "updatedAt", r.recorded_at AS "recordedAt"
            FROM blog_novel_fragment_revision r
            WHERE r.fragment_id = (SELECT id FROM blog_novel_fragment WHERE slug = #{slug})
            ORDER BY r.revision DESC
            """)
    List<BlogContentRevisionPo> selectNovelRevisions(@Param("slug") String slug);

    @Select("""
            SELECT tag FROM blog_post_revision_tag
            WHERE post_id = #{postId} AND revision = #{revision}
            ORDER BY tag_order
            """)
    List<String> selectPostRevisionTags(
            @Param("postId") String postId, @Param("revision") long revision);
}
