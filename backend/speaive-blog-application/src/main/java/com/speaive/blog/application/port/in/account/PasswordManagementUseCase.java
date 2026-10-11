package com.speaive.blog.application.port.in.account;

import com.speaive.blog.application.command.account.ChangePasswordCommand;
import com.speaive.blog.application.command.account.ResetPasswordCommand;
import com.speaive.blog.application.result.account.PasswordResetResult;

/** 会员密码维护与会话凭证有效性检查；HTTP 身份校验和密码编码在安全入站层完成。 */
public interface PasswordManagementUseCase {
    void changePassword(String username, ChangePasswordCommand command);
    PasswordResetResult issueReset(String username);
    void resetPassword(ResetPasswordCommand command);
    boolean credentialsCurrent(String username, long credentialVersion);
}
