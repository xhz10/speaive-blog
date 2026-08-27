package com.speaive.blog.domain.post;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostAiSummaryTests {
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Test
    void tracksTheExactPostRevisionItSummarizes() {
        Post post = Post.createDraft(
                "post-id", "memory", new PostContent(
                        "文章记忆", "", NOW, List.of("写作"), null, "正文"), admin(),
                PostVisibility.PUBLIC, NOW);
        PostAiSummary summary = PostAiSummary.create(
                post.id(), post.revision(), "这是一段中立的文章摘要。", "test-model", 10, 8, NOW);

        assertTrue(summary.isCurrentFor(post));
        Post updated = post.update(
                new PostContent("文章记忆", "", NOW, List.of("写作"), null, "修改后的正文"),
                post.version(), NOW.plusSeconds(1)).current();
        assertFalse(summary.isCurrentFor(updated));
    }

    private static Author admin() {
        return new Author("admin-id", "admin", "管理员", AuthorType.HUMAN, null, AuthorStatus.ACTIVE);
    }
}
