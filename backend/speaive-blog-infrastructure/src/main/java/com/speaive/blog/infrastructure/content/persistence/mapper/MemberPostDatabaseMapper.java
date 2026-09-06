package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.speaive.blog.infrastructure.content.persistence.po.MemberPostPo;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 会员文章 SQL 边界；每个读取和更新均绑定服务端解析的所有者，公开读取额外要求发布与公开可见。 */
@Mapper
public interface MemberPostDatabaseMapper {
    String COLUMNS = "id, owner_id AS \"ownerId\", slug, status, visibility, created_at AS \"createdAt\", updated_at AS \"updatedAt\", revision, archived, payload, payload_encrypted AS \"payloadEncrypted\"";
    @Select("SELECT " + COLUMNS + " FROM blog_member_post WHERE owner_id = #{owner} AND (NOT #{published} OR (status = 'PUBLISHED' AND visibility = 'PUBLIC')) ORDER BY updated_at DESC, id LIMIT #{limit} OFFSET #{offset}")
    List<MemberPostPo> list(@Param("owner") String owner, @Param("published") boolean published, @Param("limit") int limit, @Param("offset") long offset);
    @Select("SELECT COUNT(*) FROM blog_member_post WHERE owner_id = #{owner} AND (NOT #{published} OR (status = 'PUBLISHED' AND visibility = 'PUBLIC'))")
    long count(@Param("owner") String owner, @Param("published") boolean published);
    @Select("SELECT " + COLUMNS + " FROM blog_member_post WHERE owner_id = #{owner} AND slug = #{slug} AND (NOT #{published} OR (status = 'PUBLISHED' AND visibility = 'PUBLIC'))")
    MemberPostPo find(@Param("owner") String owner, @Param("slug") String slug, @Param("published") boolean published);
    @Insert("INSERT INTO blog_member_post (id, owner_id, slug, status, visibility, created_at, updated_at, revision, archived, payload, payload_encrypted) VALUES (#{row.id}, #{row.ownerId}, #{row.slug}, #{row.status}, #{row.visibility}, #{row.createdAt}, #{row.updatedAt}, #{row.revision}, #{row.archived}, #{row.payload}, #{row.payloadEncrypted})")
    int insert(@Param("row") MemberPostPo row);
    @Insert("INSERT INTO blog_member_post_revision (id, owner_id, slug, status, visibility, created_at, updated_at, revision, archived, payload, payload_encrypted, event_type) VALUES (#{row.id}, #{row.ownerId}, #{row.slug}, #{row.status}, #{row.visibility}, #{row.createdAt}, #{row.updatedAt}, #{row.revision}, #{row.archived}, #{row.payload}, #{row.payloadEncrypted}, #{event})")
    int insertRevision(@Param("row") MemberPostPo row, @Param("event") String event);
    @Update("UPDATE blog_member_post SET status = #{row.status}, visibility = #{row.visibility}, updated_at = #{row.updatedAt}, revision = #{row.revision}, payload = #{row.payload}, payload_encrypted = #{row.payloadEncrypted} WHERE id = #{row.id} AND owner_id = #{row.ownerId} AND slug = #{row.slug} AND revision = #{expected}")
    int update(@Param("row") MemberPostPo row, @Param("expected") long expected);
    @Delete("DELETE FROM blog_member_post WHERE id = #{row.id} AND owner_id = #{row.ownerId} AND slug = #{row.slug} AND revision = #{expected}")
    int delete(@Param("row") MemberPostPo row, @Param("expected") long expected);
    @Select("SELECT " + COLUMNS + " FROM blog_member_post_revision WHERE owner_id = #{owner} AND id = #{id} ORDER BY revision DESC LIMIT 50")
    List<MemberPostPo> revisions(@Param("owner") String owner, @Param("id") String id);
    @Select("SELECT " + COLUMNS + " FROM blog_member_post_revision WHERE owner_id = #{owner} AND id = #{id} AND revision = #{revision}")
    MemberPostPo revision(@Param("owner") String owner, @Param("id") String id, @Param("revision") long revision);
    // 分批读取全部当前及历史行，不能复用只取最近 50 条的历史列表；包含已经归档的文章。
    @Select("SELECT " + COLUMNS + " FROM blog_member_post WHERE owner_id = #{owner} ORDER BY id LIMIT 100 OFFSET #{offset}")
    List<MemberPostPo> protectionPosts(@Param("owner") String owner, @Param("offset") long offset);
    @Select("SELECT " + COLUMNS + " FROM blog_member_post_revision WHERE owner_id = #{owner} ORDER BY id, revision LIMIT 100 OFFSET #{offset}")
    List<MemberPostPo> protectionRevisions(@Param("owner") String owner, @Param("offset") long offset);
    @Update("UPDATE blog_member_post SET payload = #{payload}, payload_encrypted = #{encrypted} WHERE owner_id = #{row.ownerId} AND id = #{row.id} AND revision = #{row.revision}")
    int protectPost(@Param("row") MemberPostPo row, @Param("payload") String payload, @Param("encrypted") boolean encrypted);
    @Update("UPDATE blog_member_post_revision SET payload = #{payload}, payload_encrypted = #{encrypted} WHERE owner_id = #{row.ownerId} AND id = #{row.id} AND revision = #{row.revision}")
    int protectRevision(@Param("row") MemberPostPo row, @Param("payload") String payload, @Param("encrypted") boolean encrypted);
}
