package com.speaive.blog.infrastructure.content;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BlogMediaDatabaseMapper {
    @Insert("""
            INSERT INTO blog_media (
                relative_path, original_file_name, mime_type, size_bytes, sha256, created_at
            ) VALUES (
                #{media.relativePath}, #{media.originalFileName}, #{media.mimeType},
                #{media.sizeBytes}, #{media.sha256}, #{media.createdAt}
            )
            """)
    int insert(@Param("media") BlogMediaPo media);

    @Select("""
            SELECT relative_path, original_file_name, mime_type, size_bytes, sha256, created_at
            FROM blog_media
            WHERE relative_path = #{relativePath}
            """)
    BlogMediaPo selectByPath(@Param("relativePath") String relativePath);
}
