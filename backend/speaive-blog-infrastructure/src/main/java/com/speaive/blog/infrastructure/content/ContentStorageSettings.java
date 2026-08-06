package com.speaive.blog.infrastructure.content;

import java.nio.file.Path;

public record ContentStorageSettings(Path dataDirectory, long maxMarkdownBytes, long maxImageBytes) {
    public ContentStorageSettings {
        dataDirectory = dataDirectory.toAbsolutePath().normalize();
        if (maxMarkdownBytes <= 0 || maxImageBytes <= 0) {
            throw new IllegalArgumentException("文件大小限制必须大于 0");
        }
    }
}
