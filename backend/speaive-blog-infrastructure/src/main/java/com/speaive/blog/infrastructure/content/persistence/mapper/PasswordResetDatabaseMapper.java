package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.PasswordResetPo;
import org.apache.ibatis.annotations.*;
import java.time.Instant;

@Mapper
public interface PasswordResetDatabaseMapper extends BaseMapper<PasswordResetPo> {
    @Select("SELECT * FROM blog_password_reset WHERE token_hash = #{tokenHash} FOR UPDATE")
    PasswordResetPo lockByTokenHash(@Param("tokenHash") String tokenHash);
    @Update("""
            UPDATE blog_password_reset SET consumed_at = #{reset.consumedAt}
            WHERE token_hash = #{reset.tokenHash} AND consumed_at IS NULL
            """)
    int consume(@Param("reset") PasswordResetPo reset);
    @Update("""
            UPDATE blog_password_reset SET consumed_at = #{now}
            WHERE account_id = #{accountId} AND consumed_at IS NULL
            """)
    int revokeForAccount(@Param("accountId") String accountId, @Param("now") Instant now);
}
