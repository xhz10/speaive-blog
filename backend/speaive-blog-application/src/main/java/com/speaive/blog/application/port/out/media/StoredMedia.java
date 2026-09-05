package com.speaive.blog.application.port.out.media;

/**
 * 媒体存储完成后的地址、路径和校验信息，不暴露底层文件句柄。
 */
public record StoredMedia(String url, String relativePath, String mimeType, long size) {
}
