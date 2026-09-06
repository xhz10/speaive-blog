package com.speaive.blog.infrastructure.content.persistence.po;

import java.time.Instant;
import java.util.List;

/** 加密载荷：所有作者填写的文本整体编码，禁止将标题、摘要或标签另存为明文索引。 */
public record MemberPostContentPo(String title, String description, Instant publishedAt,
        List<String> tags, String cover, String body) { }
