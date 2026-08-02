package com.speaive.blog.infrastructure.content;

import java.nio.file.Path;

@FunctionalInterface
interface BeforeFileCommit {
    void beforeCommit(Path source, Path target, boolean replaceExisting);
}
