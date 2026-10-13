package com.speaive.blog.application.port.out.knowledge;

import com.speaive.blog.domain.knowledge.HumorMechanism;

/** mechanism 可空，供管理员预览所有机制；评论生成传入绑定机制。 */
public record HumorSearchQuery(String text, HumorMechanism mechanism, int limit) {
    public HumorSearchQuery {
        text = text == null ? "" : text.trim();
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("检索条数应为 1 到 50");
        if (text.codePointCount(0, text.length()) > 1000) throw new IllegalArgumentException("检索文本过长");
    }
}
