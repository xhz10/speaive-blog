package com.speaive.blog.application.command.account;

/** 安全适配层验证旧密码后传入的版本与新密码哈希，不传递密码原文。 */
public record ChangePasswordCommand(long verifiedCredentialVersion, String newPasswordHash) {}
