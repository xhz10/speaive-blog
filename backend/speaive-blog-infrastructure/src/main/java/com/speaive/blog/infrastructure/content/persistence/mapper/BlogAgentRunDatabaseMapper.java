package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAgentRunPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface BlogAgentRunDatabaseMapper extends BaseMapper<BlogAgentRunPo> {
    @Update("""
            UPDATE blog_agent_run
            SET model = #{run.model}, status = #{run.status}, comment_id = #{run.commentId},
                input_tokens = #{run.inputTokens}, output_tokens = #{run.outputTokens},
                error_message = #{run.errorMessage}, completed_at = #{run.completedAt}
            WHERE id = #{run.id} AND status = 'RUNNING'
            """)
    int finish(@Param("run") BlogAgentRunPo run);

    @Update("""
            UPDATE blog_agent_run
            SET status = 'FAILED', error_message = '执行中断或超时，可重新生成', completed_at = #{completedAt}
            WHERE post_id = #{postId} AND post_revision = #{postRevision} AND agent_id = #{agentId}
              AND target_comment_id IS NOT DISTINCT FROM #{targetCommentId}
              AND status = 'RUNNING' AND started_at < #{startedBefore}
            """)
    int failStaleRunning(
            @Param("postId") String postId,
            @Param("postRevision") long postRevision,
            @Param("agentId") String agentId,
            @Param("targetCommentId") String targetCommentId,
            @Param("startedBefore") Instant startedBefore,
            @Param("completedAt") Instant completedAt);
}
