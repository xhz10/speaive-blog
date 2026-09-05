package com.speaive.blog.application.port.in.account;

import com.speaive.blog.application.command.account.CreateInvitationCommand;
import com.speaive.blog.application.command.account.RegisterMemberCommand;
import com.speaive.blog.application.result.account.AccountCredentialsResult;
import com.speaive.blog.application.result.account.InvitationListResult;
import com.speaive.blog.application.result.account.InvitationResult;
import com.speaive.blog.application.result.account.MemberResult;

import java.util.Optional;

/**
 * 会员与邀请码入站契约，供注册、登录凭证读取和站长邀请管理使用。
 */
public interface MemberAccountUseCase {
    MemberResult register(RegisterMemberCommand command);

    MemberResult getMember(String username);

    Optional<AccountCredentialsResult> findCredentials(String username);

    InvitationResult createInvitation(CreateInvitationCommand command);

    InvitationListResult listInvitations();
}
