package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.automation.CommunityCommentJob;

import java.time.Instant;
import java.util.Optional;

public interface CommunityCommentJobRepository {
    boolean addIfAbsent(CommunityCommentJob job);

    Optional<CommunityCommentJob> claimNext(Instant now, Instant staleBefore);

    void save(CommunityCommentJob job);
}
