package com.speaive.blog.application.port.out;

import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.application.support.MarkdownParseRequest;
import com.speaive.blog.application.support.ParsedPostDocument;

import java.time.Instant;

public interface MarkdownPort {
    ParsedPostDocument parse(MarkdownParseRequest request);

    ParsedPostDocument normalize(PostWriteCommand command, String slug, Instant publishedAt);

    String render(String markdown);
}
