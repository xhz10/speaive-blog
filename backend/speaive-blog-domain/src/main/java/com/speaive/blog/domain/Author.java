package com.speaive.blog.domain;

import java.util.Objects;

public record Author(
        String id,
        String username,
        String displayName,
        AuthorType type,
        String avatarUrl
) {
    public static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    public static final Author ADMIN = new Author(ADMIN_ID, "admin", "Speaive", AuthorType.HUMAN, null);

    public Author {
        id = Objects.requireNonNull(id, "id");
        username = Objects.requireNonNull(username, "username");
        displayName = Objects.requireNonNull(displayName, "displayName");
        type = Objects.requireNonNull(type, "type");
    }
}
