package com.speaive.blog.application.result.account;

import java.util.List;
/** 管理员可以管理的会员账号列表。 */
public record WritingAccountListResult(List<WritingAccountResult> items) {}
