package com.speaive.blog.application.result.account;

import java.time.Instant;

/** 仅签发时展示原始令牌，响应不得缓存。 */
public record PasswordResetResult(String username, String token, Instant expiresAt) {}
