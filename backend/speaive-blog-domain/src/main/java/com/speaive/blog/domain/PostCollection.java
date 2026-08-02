package com.speaive.blog.domain;

import java.util.List;

public record PostCollection(List<Post> items, List<ContentError> errors) {
    public PostCollection {
        items = List.copyOf(items);
        errors = List.copyOf(errors);
    }
}
