package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface BlogAuthorDatabaseMapper extends BaseMapper<BlogAuthorPo> {
    @Insert("""
            INSERT INTO blog_user (
                id, username, display_name, type, status, avatar_url, created_at, updated_at
            ) VALUES (
                #{author.id}, #{author.username}, #{author.displayName}, #{author.type}, #{author.status},
                #{author.avatarUrl}, #{createdAt}, #{createdAt}
            )
            """)
    int insertAgentAuthor(@Param("author") BlogAuthorPo author, @Param("createdAt") Instant createdAt);

    @Update("""
            UPDATE blog_user
            SET display_name = #{author.displayName}, avatar_url = #{author.avatarUrl},
                status = #{author.status}, updated_at = #{updatedAt}
            WHERE id = #{author.id} AND type = 'AGENT'
            """)
    int updateAgentAuthor(@Param("author") BlogAuthorPo author, @Param("updatedAt") Instant updatedAt);
}
