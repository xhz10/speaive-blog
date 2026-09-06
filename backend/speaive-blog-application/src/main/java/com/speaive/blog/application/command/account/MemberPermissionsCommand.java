package com.speaive.blog.application.command.account;

/** 管理员授予作者、发布及加密资格；version 来自账号设置响应。 */
public record MemberPermissionsCommand(String role, boolean canPublish, boolean encryptionAllowed, long version) {}
