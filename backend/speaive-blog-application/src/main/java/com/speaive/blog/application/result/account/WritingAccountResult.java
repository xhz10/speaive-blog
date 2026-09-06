package com.speaive.blog.application.result.account;

/** 写作权限与存储设置，不包含密码哈希或密钥。 */
public record WritingAccountResult(String id, String username, String displayName, String role,
        boolean canPublish, boolean encryptionAllowed, boolean contentEncrypted, boolean encryptionAvailable, long version) {}
