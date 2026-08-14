package com.speaive.blog.infrastructure.content.markdown;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.Encoding;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownCodec {
    private static final Pattern FIRST_HEADING = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final Pattern LEADING_HEADING = Pattern.compile("^#\\s+(.+?)\\s*(?:\\R|$)");
    private static final Pattern COVER_PATTERN = Pattern.compile(
            "^/media/[A-Za-z0-9/_-]+\\.(?:avif|gif|jpe?g|png|webp)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create()
    );
    private static final PolicyFactory HTML_POLICY = new HtmlPolicyBuilder()
            .allowElements("a", "blockquote", "br", "code", "del", "details", "em", "h1", "h2", "h3",
                    "h4", "h5", "h6", "hr", "img", "li", "ol", "p", "pre", "strong", "summary",
                    "table", "tbody", "td", "th", "thead", "tr", "ul")
            .allowAttributes("href", "title").onElements("a")
            .allowAttributes("class").matching(Pattern.compile("^language-[A-Za-z0-9_+.-]+$"))
            .onElements("code")
            .allowAttributes("src", "alt", "title", "loading").onElements("img")
            .allowUrlProtocols("http", "https", "mailto")
            .toFactory();

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(false)
            .build();

    private static Yaml newYamlReader() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setAllowRecursiveKeys(false);
        loaderOptions.setMaxAliasesForCollections(20);
        loaderOptions.setCodePointLimit(1_100_000);
        return new Yaml(new SafeConstructor(loaderOptions));
    }

    private static Yaml newYamlWriter() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setSplitLines(false);
        dumperOptions.setIndent(2);
        return new Yaml(dumperOptions);
    }

    ParsedMarkdown parse(byte[] source, ParseOptions options) {
        String text = decodeUtf8(source);
        FrontMatter frontMatter = splitFrontMatter(text);
        Map<String, Object> metadata = parseMetadata(frontMatter.yaml());

        String fallbackTitle = firstHeading(frontMatter.body());
        if (fallbackTitle == null || fallbackTitle.isBlank()) {
            fallbackTitle = options.fallbackTitle();
        }
        String slug = optionalString(metadata.get("slug"));
        if (slug == null) {
            slug = options.fallbackSlug();
        }
        slug = validateSlug(slug);
        if (options.expectedSlug() != null && !options.expectedSlug().equals(slug)) {
            throw invalidMarkdown("frontmatter slug 与文件名不一致：" + slug);
        }

        String title = optionalString(metadata.get("title"));
        if (title == null) {
            title = fallbackTitle;
        }
        title = requireText(title, "标题不能为空", 200);

        String description = optionalString(metadata.get("description"));
        if (description == null) {
            description = optionalString(metadata.get("summary"));
        }
        Instant publishedAt = parseInstant(metadata.get("publishedAt"), options.fallbackPublishedAt(), "发布时间");
        Instant updatedAt = parseOptionalInstant(metadata.get("updatedAt"), "更新时间");
        List<String> tags = parseTags(metadata.get("tags"));
        String cover = validateCover(optionalString(metadata.get("cover")));
        String body = removeLeadingTitleHeading(frontMatter.body(), title);
        if (description == null || description.isBlank()) {
            description = extractDescription(body, 180);
        }
        description = requireOptionalText(description, "摘要过长", 500);

        return new ParsedMarkdown(slug, title, description, publishedAt, updatedAt, tags, cover, body,
                render(body));
    }

    byte[] serialize(PostDocument document) {
        String slug = validateSlug(document.slug());
        String title = requireText(document.title(), "标题不能为空", 200);
        String description = requireOptionalText(document.description(), "摘要过长", 500);
        if (document.publishedAt() == null) {
            throw invalidMarkdown("发布时间不能为空");
        }
        List<String> tags = validateTags(document.tags());
        String cover = validateCover(document.cover());
        String body = Objects.requireNonNullElse(document.body(), "").trim();
        if (body.indexOf('\0') >= 0) {
            throw invalidMarkdown("正文包含非法字符");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title);
        metadata.put("slug", slug);
        metadata.put("description", description);
        metadata.put("publishedAt", document.publishedAt().toString());
        metadata.put("updatedAt", Objects.requireNonNull(document.updatedAt(), "更新时间不能为空").toString());
        metadata.put("tags", tags);
        if (cover != null) {
            metadata.put("cover", cover);
        }
        String yaml = newYamlWriter().dump(metadata);
        return ("---\n" + yaml + "---\n\n" + body + "\n").getBytes(StandardCharsets.UTF_8);
    }

    String render(String markdown) {
        String source = Objects.requireNonNullElse(markdown, "");
        if (source.indexOf('\0') >= 0) {
            throw invalidMarkdown("Markdown 包含非法字符");
        }
        Node document = parser.parse(source);
        String unsafeHtml = renderer.render(document);
        return HTML_POLICY.sanitize(unsafeHtml);
    }

    static String validateSlug(String value) {
        if (value == null) {
            throw invalidMarkdown("文章缺少 slug");
        }
        String slug = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        int length = slug.codePointCount(0, slug.length());
        if (length == 0 || length > 100
                || !slug.matches("^[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*$")) {
            throw new BlogException(BlogErrorCode.INVALID_FILE_NAME,
                    "slug 只允许文字、数字和单个连字符，且不能超过 100 个字符");
        }
        return slug;
    }

    private Map<String, Object> parseMetadata(String yaml) {
        if (yaml == null) {
            return Map.of();
        }
        try {
            Object loaded = newYamlReader().load(yaml);
            if (loaded == null) {
                return Map.of();
            }
            if (!(loaded instanceof Map<?, ?> rawMap)) {
                throw invalidMarkdown("Markdown frontmatter 必须是键值对象");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw invalidMarkdown("Markdown frontmatter 字段名必须是字符串");
                }
                result.put(key, entry.getValue());
            }
            return result;
        } catch (BlogException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BlogException(BlogErrorCode.INVALID_MARKDOWN, "Markdown frontmatter 无法解析", exception);
        }
    }

    private static FrontMatter splitFrontMatter(String text) {
        String normalized = text.startsWith("\uFEFF") ? text.substring(1) : text;
        if (!(normalized.startsWith("---\n") || normalized.startsWith("---\r\n"))) {
            return new FrontMatter(null, normalized);
        }
        int firstLineEnd = normalized.indexOf('\n');
        int cursor = firstLineEnd + 1;
        while (cursor < normalized.length()) {
            int lineEnd = normalized.indexOf('\n', cursor);
            if (lineEnd < 0) {
                lineEnd = normalized.length();
            }
            String line = normalized.substring(cursor, lineEnd).stripTrailing();
            if (line.equals("---")) {
                String header = normalized.substring(firstLineEnd + 1, cursor);
                String body = lineEnd < normalized.length() ? normalized.substring(lineEnd + 1) : "";
                return new FrontMatter(header, body);
            }
            cursor = lineEnd + 1;
        }
        throw invalidMarkdown("Markdown frontmatter 缺少结束标记");
    }

    private static String decodeUtf8(byte[] source) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(source))
                    .toString();
            if (decoded.indexOf('\0') >= 0) {
                throw invalidMarkdown("Markdown 包含非法字符");
            }
            return decoded;
        } catch (CharacterCodingException exception) {
            throw new BlogException(BlogErrorCode.INVALID_MARKDOWN, "Markdown 必须使用 UTF-8 编码", exception);
        }
    }

    private static String firstHeading(String body) {
        Matcher matcher = FIRST_HEADING.matcher(body);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String removeLeadingTitleHeading(String body, String title) {
        String trimmed = body.trim();
        Matcher matcher = LEADING_HEADING.matcher(trimmed);
        if (!matcher.find() || !matcher.group(1).trim().equals(title.trim())) {
            return trimmed;
        }
        return trimmed.substring(matcher.end()).trim();
    }

    private String extractDescription(String markdown, int maxLength) {
        String plain = Encoding.decodeHtml(
                        render(markdown).replaceAll("<[^>]+>", " "),
                        false
                )
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.isEmpty()) {
            return "";
        }
        int codePoints = plain.codePointCount(0, plain.length());
        if (codePoints <= maxLength) {
            return plain;
        }
        int end = plain.offsetByCodePoints(0, maxLength - 3);
        return plain.substring(0, end).stripTrailing() + "...";
    }

    private static Instant parseInstant(Object value, Instant fallback, String fieldName) {
        Instant result = parseOptionalInstant(value, fieldName);
        if (result != null) {
            return result;
        }
        if (fallback == null) {
            throw invalidMarkdown(fieldName + "不能为空");
        }
        return fallback;
    }

    private static Instant parseOptionalInstant(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        String text = String.valueOf(value).trim();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException exception) {
                throw new BlogException(BlogErrorCode.INVALID_MARKDOWN,
                        fieldName + "必须是带时区的 ISO-8601 日期", exception);
            }
        }
    }

    private static List<String> parseTags(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw invalidMarkdown("tags 必须是数组");
        }
        List<String> tags = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String tag)) {
                throw invalidMarkdown("标签必须是字符串");
            }
            tags.add(tag);
        }
        return validateTags(tags);
    }

    private static List<String> validateTags(List<String> values) {
        List<String> tags = values == null ? List.of() : values;
        if (tags.size() > 20) {
            throw invalidMarkdown("标签不能超过 20 个");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : tags) {
            String tag = requireText(value, "标签不能为空", 40);
            unique.add(tag);
        }
        return List.copyOf(unique);
    }

    private static String validateCover(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cover = value.trim();
        if (!COVER_PATTERN.matcher(cover).matches() || cover.contains("..") || cover.contains("//")) {
            throw invalidMarkdown("封面必须使用 /media/ 下的已上传图片");
        }
        return cover;
    }

    private static String optionalString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String requireText(String value, String message, int maxLength) {
        String result = value == null ? "" : value.trim();
        int length = result.codePointCount(0, result.length());
        if (length == 0 || length > maxLength) {
            throw invalidMarkdown(length == 0 ? message : message.replace("不能为空", "过长"));
        }
        return result;
    }

    private static String requireOptionalText(String value, String message, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.codePointCount(0, result.length()) > maxLength) {
            throw invalidMarkdown(message);
        }
        return result;
    }

    private static BlogException invalidMarkdown(String message) {
        return new BlogException(BlogErrorCode.INVALID_MARKDOWN, message);
    }

    record ParseOptions(String expectedSlug, String fallbackSlug, String fallbackTitle, Instant fallbackPublishedAt) {
    }

    record ParsedMarkdown(
            String slug,
            String title,
            String description,
            Instant publishedAt,
            Instant updatedAt,
            List<String> tags,
            String cover,
            String body,
            String html
    ) {
    }

    record PostDocument(
            String slug,
            String title,
            String description,
            Instant publishedAt,
            Instant updatedAt,
            List<String> tags,
            String cover,
            String body
    ) {
    }

    private record FrontMatter(String yaml, String body) {
    }
}
