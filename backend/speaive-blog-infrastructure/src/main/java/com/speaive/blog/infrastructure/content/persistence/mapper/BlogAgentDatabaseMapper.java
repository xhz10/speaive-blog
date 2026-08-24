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
            SELECT id, system_prompt AS "systemPrompt", model, temperature,
                   can_process_private AS "canProcessPrivate", prompt_version AS "promptVersion",
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_agent
            ORDER BY created_at, id
            """)
    List<BlogAgentPo> selectAllAgents();

    @Select("""
            SELECT id, system_prompt AS "systemPrompt", model, temperature,
                   can_process_private AS "canProcessPrivate", prompt_version AS "promptVersion",
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_agent
            WHERE id = #{id}
            """)
    BlogAgentPo selectAgentById(@Param("id") String id);

    @Update("""
            UPDATE blog_agent
            SET system_prompt = #{agent.systemPrompt}, model = #{agent.model},
                temperature = #{agent.temperature}, can_process_private = #{agent.canProcessPrivate},
                prompt_version = #{agent.promptVersion}, updated_at = #{agent.updatedAt}
            WHERE id = #{agent.id} AND prompt_version = #{expectedVersion}
            """)
    int updateCas(@Param("agent") BlogAgentPo agent, @Param("expectedVersion") long expectedVersion);
}
