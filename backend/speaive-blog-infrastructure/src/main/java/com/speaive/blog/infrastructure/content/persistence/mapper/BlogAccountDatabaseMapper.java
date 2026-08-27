package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAccountPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BlogAccountDatabaseMapper extends BaseMapper<BlogAccountPo> {
    @Select("""
            SELECT account.id, account.password_hash AS "passwordHash",
                   account.created_at AS "createdAt", account.updated_at AS "updatedAt"
            FROM blog_account account
            JOIN blog_user author ON author.id = account.id
            WHERE LOWER(author.username) = LOWER(#{username})
            """)
    BlogAccountPo selectByUsername(@Param("username") String username);

    @Select("""
            SELECT account.id, account.password_hash AS "passwordHash",
                   account.created_at AS "createdAt", account.updated_at AS "updatedAt"
            FROM blog_account account
            JOIN blog_user author ON author.id = account.id
            WHERE LOWER(author.username) = LOWER(#{username})
            FOR UPDATE OF account
            """)
    BlogAccountPo lockByUsername(@Param("username") String username);
}
