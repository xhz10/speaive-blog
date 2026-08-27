package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogCommunityCommentJobPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface BlogCommunityCommentJobDatabaseMapper extends BaseMapper<BlogCommunityCommentJobPo> {
    @Select("""
            WITH candidate AS (
                SELECT id
                FROM blog_community_comment_job
                WHERE (status = 'PENDING' AND available_at <= #{now})
                   OR (status = 'RUNNING' AND claimed_at < #{staleBefore})
                ORDER BY available_at, created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE blog_community_comment_job job
            SET status = 'RUNNING', attempts = job.attempts + 1,
                claimed_at = #{now}, completed_at = NULL, updated_at = #{now}
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id, job.post_id AS "postId", job.post_revision AS "postRevision",
                      job.agent_id AS "agentId", job.status, job.attempts,
                      job.available_at AS "availableAt", job.claimed_at AS "claimedAt",
                      job.completed_at AS "completedAt", job.last_error AS "lastError",
                      job.created_at AS "createdAt", job.updated_at AS "updatedAt"
            """)
    BlogCommunityCommentJobPo claimNext(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore);

    @Update("""
            UPDATE blog_community_comment_job
            SET status = #{job.status}, available_at = #{job.availableAt},
                claimed_at = #{job.claimedAt}, completed_at = #{job.completedAt},
                last_error = #{job.lastError}, updated_at = #{job.updatedAt}
            WHERE id = #{job.id} AND status = 'RUNNING' AND attempts = #{job.attempts}
            """)
    int finish(@Param("job") BlogCommunityCommentJobPo job);
}
