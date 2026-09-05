package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.account.Invitation;

import java.util.List;
import java.util.Optional;

/**
 * 邀请凭证持久化端口，支持按哈希查询并在事务内锁定、更新使用次数。
 */
public interface InvitationRepository {
    List<Invitation> findAll();

    Optional<Invitation> lockByCodeHash(String codeHash);

    void add(Invitation invitation);

    void save(Invitation invitation, int expectedUsedCount);
}
