package com.speaive.blog.application.command.account;

/** 作者自己的存储选择，切换会覆盖当前文章和历史修订，不能由请求指定其他所有者。 */
public record ContentEncryptionCommand(boolean encrypted, long version) {}
