package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAccountPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BlogAccountDatabaseMapper extends BaseMapper<BlogAccountPo> {
    @Select("""
            SELECT account.id, account.password_hash AS "passwordHash",
                   account.created_at AS "createdAt", account.updated_at AS "updatedAt",
                   account.role, account.can_publish AS "canPublish", account.encryption_allowed AS "encryptionAllowed",
                   account.content_encrypted AS "contentEncrypted", account.settings_version AS "settingsVersion",
                   account.credential_version AS "credentialVersion"
            FROM blog_account account
            JOIN blog_user author ON author.id = account.id
            WHERE LOWER(author.username) = LOWER(#{username})
            """)
    BlogAccountPo selectByUsername(@Param("username") String username);

    @Select("""
            SELECT account.id, account.password_hash AS "passwordHash",
                   account.created_at AS "createdAt", account.updated_at AS "updatedAt",
                   account.role, account.can_publish AS "canPublish", account.encryption_allowed AS "encryptionAllowed",
                   account.content_encrypted AS "contentEncrypted", account.settings_version AS "settingsVersion",
                   account.credential_version AS "credentialVersion"
            FROM blog_account account
            JOIN blog_user author ON author.id = account.id
            WHERE LOWER(author.username) = LOWER(#{username})
            FOR UPDATE OF account, author
            """)
    BlogAccountPo lockByUsername(@Param("username") String username);
    @Select("""
            SELECT account.* FROM blog_account account
            JOIN blog_user author ON author.id = account.id
            WHERE account.id = #{id}
            FOR UPDATE OF account, author
            """)
    BlogAccountPo lockAccountById(@Param("id") String id);

    @Update("""
            UPDATE blog_account SET password_hash = #{account.passwordHash},
                credential_version = #{account.credentialVersion}, updated_at = #{account.updatedAt}
            WHERE id = #{account.id} AND credential_version = #{expectedVersion}
            """)
    int updatePassword(@Param("account") BlogAccountPo account, @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE blog_account SET role = #{account.role}, can_publish = #{account.canPublish},
                encryption_allowed = #{account.encryptionAllowed}, content_encrypted = #{account.contentEncrypted},
                settings_version = #{account.settingsVersion}, updated_at = #{account.updatedAt}
            WHERE id = #{account.id} AND settings_version = #{expectedVersion}
            """)
    int updateSettings(@Param("account") BlogAccountPo account, @Param("expectedVersion") long expectedVersion);
}
