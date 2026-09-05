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

    void add(MemberAccount account);
}
