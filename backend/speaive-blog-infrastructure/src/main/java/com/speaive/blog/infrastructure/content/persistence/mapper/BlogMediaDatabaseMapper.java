package com.speaive.blog.infrastructure.content.persistence.mapper;

import com.speaive.blog.infrastructure.content.persistence.po.BlogMediaPo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    @Select("""
            <script>
            SELECT relative_path
            FROM blog_media
            WHERE relative_path IN
            <foreach collection="relativePaths" item="path" open="(" separator="," close=")">
                #{path}
            </foreach>
            </script>
            """)
    List<String> selectRegisteredPaths(@Param("relativePaths") List<String> relativePaths);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM blog_post_media pm
                JOIN blog_post p ON p.id = pm.post_id
                WHERE pm.relative_path = #{relativePath}
                  AND p.status = 'PUBLISHED'
                  AND p.visibility = 'PUBLIC'
            )
            """)
    boolean isPublic(@Param("relativePath") String relativePath);
}
