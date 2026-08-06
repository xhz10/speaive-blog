package com.speaive.blog.infrastructure.content;

import com.speaive.blog.application.BlogErrorCode;
import com.speaive.blog.application.BlogException;
import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.application.port.out.MarkdownPort;
import com.speaive.blog.application.support.MarkdownParseRequest;
import com.speaive.blog.application.support.ParsedPostDocument;
import com.speaive.blog.infrastructure.content.MarkdownCodec.ParseOptions;
import com.speaive.blog.infrastructure.content.MarkdownCodec.ParsedMarkdown;
import com.speaive.blog.infrastructure.content.MarkdownCodec.PostDocument;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

public final class CommonMarkMarkdownAdapter implements MarkdownPort {
    private static final String MARKDOWN_EXTENSION = ".md";
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final MarkdownCodec codec = new MarkdownCodec();
    private final long maxMarkdownBytes;

    public CommonMarkMarkdownAdapter(ContentStorageSettings settings) {
        this.maxMarkdownBytes = Objects.requireNonNull(settings, "settings").maxMarkdownBytes();
    }

    @Override
    public ParsedPostDocument parse(MarkdownParseRequest request) {
        Objects.requireNonNull(request, "request");
        validateMarkdownFileName(request.fileName());
        byte[] source = request.markdown();
        assertMarkdownSize(source.length);
        String fallbackSlug = request.fileName().substring(0, request.fileName().length() - MARKDOWN_EXTENSION.length());
        ParsedMarkdown parsed = codec.parse(source, new ParseOptions(
                null,
                fallbackSlug,
                fallbackSlug.replace('-', ' '),
                request.fallbackPublishedAt()
        ));
        return toDocument(parsed);
    }

    @Override
    public ParsedPostDocument normalize(PostWriteCommand command, String slug, Instant publishedAt) {
        Objects.requireNonNull(command, "command");
        PostDocument document = new PostDocument(
                slug,
                command.title(),
                command.description(),
                publishedAt,
                publishedAt,
                command.tags(),
                command.cover(),
                command.body()
        );
        byte[] serialized = codec.serialize(document);
        assertMarkdownSize(serialized.length);
        ParsedMarkdown parsed = codec.parse(serialized, new ParseOptions(slug, slug, slug.replace('-', ' '), publishedAt));
        return toDocument(parsed);
    }

    @Override
    public String render(String markdown) {
        byte[] source = Objects.requireNonNullElse(markdown, "").getBytes(StandardCharsets.UTF_8);
        assertMarkdownSize(source.length);
        return codec.render(markdown);
    }

    private static ParsedPostDocument toDocument(ParsedMarkdown parsed) {
        return new ParsedPostDocument(
                parsed.slug(),
                parsed.title(),
                parsed.description(),
                parsed.publishedAt(),
                parsed.tags(),
                parsed.cover(),
                parsed.body()
        );
    }

    private void assertMarkdownSize(long size) {
        if (size > maxMarkdownBytes) {
            throw new BlogException(BlogErrorCode.TOO_LARGE,
                    "Markdown 文件不能超过 " + maxMarkdownBytes + " 字节");
        }
    }

    private static void validateMarkdownFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")
                || fileName.equals(".") || fileName.equals("..") || !fileName.endsWith(MARKDOWN_EXTENSION)
                || fileName.codePointCount(0, fileName.length()) > MAX_FILE_NAME_LENGTH) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME,
                    "Markdown 文件名必须是单层文件名，扩展名为小写 .md");
        }
    }
}
