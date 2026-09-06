package com.speaive.blog.infrastructure.content.persistence.mapping;

import com.speaive.blog.domain.account.MemberRole;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.post.*;
import com.speaive.blog.infrastructure.content.persistence.po.*;
import org.mapstruct.*;
import java.time.Instant;

/** 账号设置、会员文章快照和加密载荷的结构映射；加解密及业务决策均不放入映射表达式。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MemberPersistenceMapStructMapper {
    BlogAccountPo account(String id, String passwordHash, Instant createdAt, Instant updatedAt, MemberRole role,
            boolean canPublish, boolean encryptionAllowed, boolean contentEncrypted, long settingsVersion);
    MemberPostContentPo content(PostContent content);
    PostContent content(MemberPostContentPo content);
    @Mapping(target = "ownerId", source = "snapshot.author.id")
    @Mapping(target = "slug", source = "snapshot.slug.value")
    MemberPostPo stored(PostSnapshot snapshot, String payload, boolean payloadEncrypted);
    @Mapping(target = "id", source = "row.id")
    @Mapping(target = "slug", source = "row.slug")
    @Mapping(target = "status", source = "row.status")
    @Mapping(target = "visibility", source = "row.visibility")
    @Mapping(target = "createdAt", source = "row.createdAt")
    @Mapping(target = "updatedAt", source = "row.updatedAt")
    @Mapping(target = "revision", source = "row.revision")
    @Mapping(target = "archived", source = "row.archived")
    PostSnapshot snapshot(MemberPostPo row, PostContent content, Author author);
    default PostSlug slug(String value) { return PostSlug.of(value); }
}
