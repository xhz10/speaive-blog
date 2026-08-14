package com.speaive.blog.infrastructure.content.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

@Mapper
public interface MarkdownImportDatabaseMapper {
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{sha256}, 0)) IS NULL")
    boolean lockByHash(@Param("sha256") String sha256);

    @Select("SELECT COUNT(*) FROM blog_markdown_import WHERE sha256 = #{sha256}")
    int countByHash(@Param("sha256") String sha256);

    @Insert("""
            INSERT INTO blog_markdown_import (sha256, original_file_name, slug, imported_at)
            VALUES (#{sha256}, #{fileName}, #{slug}, #{importedAt})
            """)
    int insert(
            @Param("sha256") String sha256,
            @Param("fileName") String fileName,
            @Param("slug") String slug,
            @Param("importedAt") Instant importedAt);
}
