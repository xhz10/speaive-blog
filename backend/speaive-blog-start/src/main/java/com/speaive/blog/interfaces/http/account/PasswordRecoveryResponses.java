package com.speaive.blog.interfaces.http.account;

import java.time.Instant;

/** 找回令牌仅供站长在本次签发响应中复制。 */
final class PasswordRecoveryResponses {
    private PasswordRecoveryResponses() {}
    record Issued(String username, String token, Instant expiresAt) {}
}
