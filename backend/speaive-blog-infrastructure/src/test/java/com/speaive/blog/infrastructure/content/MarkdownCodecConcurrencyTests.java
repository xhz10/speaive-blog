package com.speaive.blog.infrastructure.content;

import com.speaive.blog.domain.PostVisibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownCodecConcurrencyTests {

    @Test
    void parsesAndSerializesFrontmatterConcurrently() throws Exception {
        MarkdownCodec codec = new MarkdownCodec();
        MarkdownCodec.PostDocument document = new MarkdownCodec.PostDocument(
                "parallel-note", "并发文章", "并发摘要", Instant.parse("2026-08-02T08:00:00Z"),
                Instant.parse("2026-08-02T09:00:00Z"), List.of("随记", "测试"), null,
                PostVisibility.ADMIN_ONLY, "正文");
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<MarkdownCodec.ParsedMarkdown>> futures = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        MarkdownCodec.ParsedMarkdown parsed = null;
                        for (int iteration = 0; iteration < 50; iteration++) {
                            parsed = codec.parse(codec.serialize(document),
                                    new MarkdownCodec.ParseOptions("parallel-note", "parallel-note",
                                            "并发文章", document.publishedAt(), PostVisibility.ADMIN_ONLY));
                        }
                        return parsed;
                    }))
                    .toList();
            ready.await();
            start.countDown();

            for (Future<MarkdownCodec.ParsedMarkdown> future : futures) {
                MarkdownCodec.ParsedMarkdown parsed = future.get();
                assertEquals("parallel-note", parsed.slug());
                assertEquals("并发文章", parsed.title());
                assertEquals(List.of("随记", "测试"), parsed.tags());
                assertEquals(PostVisibility.ADMIN_ONLY, parsed.visibility());
                assertEquals("正文", parsed.body());
            }
        }
    }
}
