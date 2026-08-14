package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.author.Author;

import java.util.Optional;

public interface AuthorRepository {
    Optional<Author> findById(String id);
}
