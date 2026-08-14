package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.port.out.persistence.AuthorRepository;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogAuthorDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.BlogPersistenceMapStructMapper;

import java.util.Objects;
import java.util.Optional;

public final class PostgresAuthorRepository implements AuthorRepository {
    private final BlogAuthorDatabaseMapper database;
    private final BlogPersistenceMapStructMapper mapping;

    public PostgresAuthorRepository(BlogAuthorDatabaseMapper database, BlogPersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public Optional<Author> findById(String id) {
        return Optional.ofNullable(database.selectById(id)).map(mapping::toAuthor);
    }
}
