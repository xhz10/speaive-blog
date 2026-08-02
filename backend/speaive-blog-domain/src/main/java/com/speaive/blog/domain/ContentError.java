package com.speaive.blog.domain;

public record ContentError(String file, PostStatus status, String message) {
}
