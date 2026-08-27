package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogCommunityPostPolicyPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BlogCommunityPostPolicyDatabaseMapper extends BaseMapper<BlogCommunityPostPolicyPo> {
    @Select("""
            SELECT post_id AS "postId", enabled, version,
                   created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_post_community_policy
            WHERE post_id = #{postId}
            FOR UPDATE
            """)
    BlogCommunityPostPolicyPo lockByPostId(@Param("postId") String postId);

    @Update("""
            UPDATE blog_post_community_policy
            SET enabled = #{policy.enabled}, version = #{policy.version}, updated_at = #{policy.updatedAt}
            WHERE post_id = #{policy.postId} AND version = #{expectedVersion}
            """)
    int updateCas(
            @Param("policy") BlogCommunityPostPolicyPo policy,
            @Param("expectedVersion") long expectedVersion);
}
