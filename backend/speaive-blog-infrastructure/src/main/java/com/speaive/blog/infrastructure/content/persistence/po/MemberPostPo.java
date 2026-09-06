package com.speaive.blog.infrastructure.content.persistence.po;

import java.time.Instant;

/** 会员文章数据库行；只把路由、所有权、访问控制和并发所需元数据保留在载荷之外。 */
public record MemberPostPo(String id, String ownerId, String slug, String status, String visibility,
        Instant createdAt, Instant updatedAt, long revision, boolean archived, String payload, boolean payloadEncrypted) { }
