package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.account.MemberAccount;

import java.util.Optional;

public interface AccountRepository {
    Optional<MemberAccount> findByUsername(String username);

    Optional<MemberAccount> findById(String id);

    Optional<MemberAccount> lockByUsername(String username);

    void add(MemberAccount account);
}
