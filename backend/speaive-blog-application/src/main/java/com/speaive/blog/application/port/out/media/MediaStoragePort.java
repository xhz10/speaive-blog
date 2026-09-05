package com.speaive.blog.application.port.out.media;

/**
 * 媒体存储出站端口，负责上传、读取及引用完整性；实现需协调文件与数据库并在失败时补偿。
 */
public interface MediaStoragePort {
    StoredMedia store(String fileName, String declaredMimeType, byte[] bytes);

    MediaContent read(String relativePath, MediaReadScope scope);
}
