package com.speaive.blog.application.port.in.account;

import com.speaive.blog.application.command.account.*;
import com.speaive.blog.application.result.account.*;

/** 账号写作与加密配置入口；管理员授予资格，会员只能变更本人的存储偏好。 */
public interface WritingAccountUseCase {
    WritingAccountResult get(String authenticatedUsername);
    WritingAccountListResult listMembers();
    WritingAccountResult setPermissions(String username, MemberPermissionsCommand command);
    WritingAccountResult setEncryption(String authenticatedUsername, ContentEncryptionCommand command);
}
