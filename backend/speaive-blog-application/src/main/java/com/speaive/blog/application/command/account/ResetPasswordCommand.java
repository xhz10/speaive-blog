package com.speaive.blog.application.command.account;

/** 找回令牌与由安全适配层生成的新密码哈希。 */
public record ResetPasswordCommand(String token, String newPasswordHash) {}
