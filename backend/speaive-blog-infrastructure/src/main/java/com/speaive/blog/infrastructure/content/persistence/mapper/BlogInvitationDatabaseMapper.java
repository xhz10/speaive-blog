package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogInvitationPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BlogInvitationDatabaseMapper extends BaseMapper<BlogInvitationPo> {
    @Select("""
            SELECT id, code_hash AS "codeHash", max_uses AS "maxUses", used_count AS "usedCount",
                   expires_at AS "expiresAt", created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_invitation
            ORDER BY created_at DESC, id DESC
            """)
    List<BlogInvitationPo> selectAllInvitations();

    @Select("""
            SELECT id, code_hash AS "codeHash", max_uses AS "maxUses", used_count AS "usedCount",
                   expires_at AS "expiresAt", created_at AS "createdAt", updated_at AS "updatedAt"
            FROM blog_invitation
            WHERE code_hash = #{codeHash}
            FOR UPDATE
            """)
    BlogInvitationPo lockByCodeHash(@Param("codeHash") String codeHash);

    @Update("""
            UPDATE blog_invitation
            SET used_count = #{invitation.usedCount}, updated_at = #{invitation.updatedAt}
            WHERE id = #{invitation.id} AND used_count = #{expectedUsedCount}
            """)
    int updateUsage(
            @Param("invitation") BlogInvitationPo invitation,
            @Param("expectedUsedCount") int expectedUsedCount);
}
