package com.speaive.blog.application.result.account;

public record AccountCredentialsResult(
        String username,
        String passwordHash,
        boolean enabled
) {
}
