package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.author.Author;

import java.util.Optional;

/**
 * 内容署名身份查询端口，供应用层取得稳定作者身份；不处理登录会话。
 */
public interface AuthorRepository {
    Optional<Author> findById(String id);
}
