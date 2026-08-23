package com.speaive.blog.application.port.out.markdown;

import com.speaive.blog.application.command.post.PostWriteCommand;

import java.time.Instant;
import java.util.Set;

public interface MarkdownPort {
    ParsedPostDocument parse(MarkdownParseRequest request);

    ParsedPostDocument normalize(PostWriteCommand command, String slug, Instant publishedAt);

    String render(String markdown);

    Set<String> referencedMediaPaths(String body, String cover);
}
