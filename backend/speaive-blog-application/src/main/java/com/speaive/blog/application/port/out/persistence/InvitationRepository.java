package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.account.Invitation;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository {
    List<Invitation> findAll();

    Optional<Invitation> lockByCodeHash(String codeHash);

    void add(Invitation invitation);

    void save(Invitation invitation, int expectedUsedCount);
}
