package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.account.MemberAccount;

import java.util.Optional;

/**
 * 会员账号持久化端口，存取登录所需的账号与密码哈希，不保存密码原文。
 */
public interface AccountRepository {
    Optional<MemberAccount> findByUsername(String username);

    Optional<MemberAccount> findById(String id);

    Optional<MemberAccount> lockByUsername(String username);

    Optional<MemberAccount> lockById(String id);

    void add(MemberAccount account);

    /** 只更新密码与凭证版本，不覆盖写作资格或加密设置。 */
    void savePassword(MemberAccount account, long expectedCredentialVersion);

    /** 管理员账号列表；返回会员身份与设置，不由 HTTP 直接访问仓储。 */
    java.util.List<MemberAccount> findAll();

    /** 保存设置并检查预期版本，必须与内容加密迁移共用事务。 */
    void saveSettings(MemberAccount account, long expectedVersion);
}
