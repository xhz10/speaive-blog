package com.speaive.blog.application.port.out;

import com.speaive.blog.domain.Author;

import java.util.Optional;

public interface AuthorRepository {
    Optional<Author> findById(String id);
}
