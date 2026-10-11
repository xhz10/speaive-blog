package com.speaive.blog.interfaces.security;

import com.speaive.blog.application.result.account.AccountCredentialsResult;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/** 登录时读取的凭证版本随 Session 保留；认证后密码哈希由 Spring 擦除，不能再查询新版本替代。 */
public final class MemberUserDetails extends User {
    private static final long serialVersionUID = 1L;
    private final long credentialVersion;
    public MemberUserDetails(AccountCredentialsResult credentials) {
        super(credentials.username(), credentials.passwordHash(), credentials.enabled(), true, true, true,
                AuthorityUtils.createAuthorityList("ROLE_MEMBER"));
        credentialVersion = credentials.credentialVersion();
    }
    public long credentialVersion() { return credentialVersion; }
}
