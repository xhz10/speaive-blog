package com.speaive.blog.infrastructure.content;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarkdownCodecTests {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-02T08:00:00Z");

    private final MarkdownCodec codec = new MarkdownCodec();

    @Test
    void generatedDescriptionDecodesAllSanitizerEntities() {
        MarkdownCodec.ParsedMarkdown parsed = parseWithoutDescription(
                "正文，；：！？\"引号\" & emoji 😀 **重点**。");

        assertEquals("正文，；：！？\"引号\" & emoji 😀 重点 。", parsed.description());
        assertFalse(parsed.description().contains("&#"));
    }

    @Test
    void explicitDescriptionRemainsLiteral() {
        String source = """
                ---
                title: 摘要测试
                slug: description-test
                description: "保留 &#xff0c; 字面量"
                publishedAt: 2026-08-02T08:00:00Z
                ---

                正文，标点。
                """;

        MarkdownCodec.ParsedMarkdown parsed = parse(source);

        assertEquals("保留 &#xff0c; 字面量", parsed.description());
    }

    @Test
    void generatedDescriptionTruncatesByDecodedCodePoint() {
        MarkdownCodec.ParsedMarkdown parsed = parseWithoutDescription("😀".repeat(178) + "，结尾");

        assertEquals("😀".repeat(177) + "...", parsed.description());
        assertEquals(180, parsed.description().codePointCount(0, parsed.description().length()));
    }

    @Test
    void generatedDescriptionIsPlainTextAfterEntityDecoding() {
        MarkdownCodec.ParsedMarkdown parsed = parseWithoutDescription(
                "正文 <strong>重点</strong> &lt;safe&gt;");

        assertEquals("正文 重点 <safe>", parsed.description());
    }

    private MarkdownCodec.ParsedMarkdown parseWithoutDescription(String body) {
        return parse("""
                ---
                title: 摘要测试
                slug: description-test
                publishedAt: 2026-08-02T08:00:00Z
                ---

                %s
                """.formatted(body));
    }

    private MarkdownCodec.ParsedMarkdown parse(String source) {
        return codec.parse(
                source.getBytes(StandardCharsets.UTF_8),
                new MarkdownCodec.ParseOptions(
                        "description-test",
                        "description-test",
                        "摘要测试",
                        PUBLISHED_AT
                )
        );
    }
}
