package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.automation.CommunityCommentJob;

import java.time.Instant;
import java.util.Optional;

/**
 * 自动评论队列端口，要求幂等入队、原子领取和安全保存任务状态；数据库锁与超时任务回收细节由实现承担。
 */
public interface CommunityCommentJobRepository {
    boolean addIfAbsent(CommunityCommentJob job);

    Optional<CommunityCommentJob> claimNext(Instant now, Instant staleBefore);

    void save(CommunityCommentJob job);
}
