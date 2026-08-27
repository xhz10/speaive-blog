package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAgentPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BlogAgentDatabaseMapper extends BaseMapper<BlogAgentPo> {
    @Select("""
            SELECT id, owner_account_id AS "ownerAccountId", system_prompt AS "systemPrompt", model, temperature,
                   can_process_private AS "canProcessPrivate", enabled_requested AS "enabledRequested",
                   review_status AS "reviewStatus", review_note AS "reviewNote", reviewed_at AS "reviewedAt",
                   auto_comment_enabled AS "autoCommentEnabled",
                   auto_comment_all_posts AS "autoCommentAllPosts", prompt_version AS "promptVersion",
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_agent
            ORDER BY created_at, id
            """)
    List<BlogAgentPo> selectAllAgents();

    @Select("""
            SELECT id, owner_account_id AS "ownerAccountId", system_prompt AS "systemPrompt", model, temperature,
                   can_process_private AS "canProcessPrivate", enabled_requested AS "enabledRequested",
                   review_status AS "reviewStatus", review_note AS "reviewNote", reviewed_at AS "reviewedAt",
                   auto_comment_enabled AS "autoCommentEnabled",
                   auto_comment_all_posts AS "autoCommentAllPosts", prompt_version AS "promptVersion",
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_agent
            WHERE id = #{id}
            """)
    BlogAgentPo selectAgentById(@Param("id") String id);

    @Select("""
            SELECT id, owner_account_id AS "ownerAccountId", system_prompt AS "systemPrompt", model, temperature,
                   can_process_private AS "canProcessPrivate", enabled_requested AS "enabledRequested",
                   review_status AS "reviewStatus", review_note AS "reviewNote", reviewed_at AS "reviewedAt",
                   auto_comment_enabled AS "autoCommentEnabled",
                   auto_comment_all_posts AS "autoCommentAllPosts", prompt_version AS "promptVersion",
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_agent
            WHERE owner_account_id = #{ownerAccountId}
            ORDER BY created_at, id
            """)
    List<BlogAgentPo> selectByOwnerAccountId(@Param("ownerAccountId") String ownerAccountId);

    @Select("""
            SELECT tag
            FROM blog_agent_auto_tag
            WHERE agent_id = #{agentId}
            ORDER BY tag_order
            """)
    List<String> selectAutoTags(@Param("agentId") String agentId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM blog_agent_auto_tag WHERE agent_id = #{agentId}")
    int deleteAutoTags(@Param("agentId") String agentId);

    @org.apache.ibatis.annotations.Insert("""
            INSERT INTO blog_agent_auto_tag (agent_id, tag, tag_order)
            VALUES (#{agentId}, #{tag}, #{tagOrder})
            """)
    int insertAutoTag(
            @Param("agentId") String agentId,
            @Param("tag") String tag,
            @Param("tagOrder") int tagOrder);

    @Update("""
            UPDATE blog_agent
            SET owner_account_id = #{agent.ownerAccountId}, system_prompt = #{agent.systemPrompt}, model = #{agent.model},
                temperature = #{agent.temperature}, can_process_private = #{agent.canProcessPrivate},
                enabled_requested = #{agent.enabledRequested}, review_status = #{agent.reviewStatus},
                review_note = #{agent.reviewNote}, reviewed_at = #{agent.reviewedAt},
                auto_comment_enabled = #{agent.autoCommentEnabled},
                auto_comment_all_posts = #{agent.autoCommentAllPosts},
                prompt_version = #{agent.promptVersion}, updated_at = #{agent.updatedAt}
            WHERE id = #{agent.id} AND prompt_version = #{expectedVersion}
            """)
    int updateCas(@Param("agent") BlogAgentPo agent, @Param("expectedVersion") long expectedVersion);
}
