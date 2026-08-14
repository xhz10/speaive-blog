package com.speaive.blog.application.port.out.markdown;

import com.speaive.blog.application.command.post.PostWriteCommand;

import java.time.Instant;

public interface MarkdownPort {
    ParsedPostDocument parse(MarkdownParseRequest request);

    ParsedPostDocument normalize(PostWriteCommand command, String slug, Instant publishedAt);

    String render(String markdown);
}
