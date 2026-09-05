package com.speaive.blog.application.port.out.persistence;

import com.speaive.blog.domain.creative.CreativeContentType;
import com.speaive.blog.domain.creative.DiscussionDigest;
import com.speaive.blog.domain.creative.EditorialReview;
import com.speaive.blog.domain.creative.Inspiration;
import com.speaive.blog.domain.creative.ShareGrant;
import com.speaive.blog.domain.creative.WorkCollection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 创作工作区持久化端口，当前同时保存灵感、作品集、分享和 AI 辅助结果；能力拆分时应与对应用例一起逐步收窄。
 */
public interface CreativeWorkspaceRepository {
    List<Inspiration> findInspirations();
    Optional<Inspiration> findInspirationById(String id);
    void addInspiration(Inspiration inspiration);
    void saveInspiration(Inspiration previous, Inspiration current);

    List<WorkCollection> findWorks();
    Optional<WorkCollection> findWorkBySlug(String slug);
    void addWork(WorkCollection work);
    void saveWork(WorkCollection previous, WorkCollection current);

    List<ShareGrant> findShares(CreativeContentType contentType, String contentSlug);
    Optional<ShareGrant> findShareById(String id);
    Optional<ShareGrant> findShareByTokenHash(String tokenHash);
    void addShare(ShareGrant grant);
    void saveShare(ShareGrant previous, ShareGrant current);
    void touchShare(String id, Instant accessedAt);

    List<EditorialReview> findEditorialReviews(String postId);
    void addEditorialReview(EditorialReview review);

    Optional<DiscussionDigest> findDiscussionDigest(String postId);
    void saveDiscussionDigest(DiscussionDigest digest);
}
