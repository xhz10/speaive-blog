package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogPostAiSummaryPo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface BlogPostAiSummaryDatabaseMapper extends BaseMapper<BlogPostAiSummaryPo> {
    @Select("""
            SELECT post_id AS "postId", post_revision AS "postRevision", body, model,
                   input_tokens AS "inputTokens", output_tokens AS "outputTokens",
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_post_ai_summary
            WHERE post_id = #{postId}
            """)
    BlogPostAiSummaryPo selectByPostId(@Param("postId") String postId);

    @Select("""
            <script>
            SELECT post_id AS "postId", post_revision AS "postRevision", body, model,
                   input_tokens AS "inputTokens", output_tokens AS "outputTokens",
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_post_ai_summary
            WHERE post_id IN
            <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
            </foreach>
            </script>
            """)
    List<BlogPostAiSummaryPo> selectByPostIds(@Param("postIds") Set<String> postIds);

    @Insert("""
            INSERT INTO blog_post_ai_summary (
                post_id, post_revision, body, model, input_tokens, output_tokens, created_at, updated_at
            ) VALUES (
                #{summary.postId}, #{summary.postRevision}, #{summary.body}, #{summary.model},
                #{summary.inputTokens}, #{summary.outputTokens}, #{summary.createdAt}, #{summary.updatedAt}
            )
            ON CONFLICT (post_id) DO UPDATE SET
                post_revision = EXCLUDED.post_revision,
                body = EXCLUDED.body,
                model = EXCLUDED.model,
                input_tokens = EXCLUDED.input_tokens,
                output_tokens = EXCLUDED.output_tokens,
                updated_at = EXCLUDED.updated_at
            WHERE blog_post_ai_summary.post_revision <= EXCLUDED.post_revision
            """)
    int upsert(@Param("summary") BlogPostAiSummaryPo summary);
}
