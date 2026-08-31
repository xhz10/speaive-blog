package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.persistence.CreativeWorkspaceRepository;
import com.speaive.blog.domain.creative.CreativeContentType;
import com.speaive.blog.domain.creative.DiscussionDigest;
import com.speaive.blog.domain.creative.EditorialReview;
import com.speaive.blog.domain.creative.Inspiration;
import com.speaive.blog.domain.creative.ShareGrant;
import com.speaive.blog.domain.creative.WorkCollection;
import com.speaive.blog.domain.creative.WorkItem;
import com.speaive.blog.infrastructure.content.persistence.mapper.BlogCreativeDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.CreativePersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.po.BlogWorkItemPo;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresCreativeWorkspaceRepository implements CreativeWorkspaceRepository {
    private final BlogCreativeDatabaseMapper database;
    private final CreativePersistenceMapStructMapper mapping;

    public PostgresCreativeWorkspaceRepository(
            BlogCreativeDatabaseMapper database,
            CreativePersistenceMapStructMapper mapping) {
        this.database = Objects.requireNonNull(database, "database");
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public List<Inspiration> findInspirations() {
        return database.selectInspirations().stream().map(mapping::toInspiration).toList();
    }

    @Override
    public Optional<Inspiration> findInspirationById(String id) {
        return Optional.ofNullable(database.selectInspiration(id)).map(mapping::toInspiration);
    }

    @Override
    public void addInspiration(Inspiration inspiration) {
        if (database.insertInspiration(mapping.toInspirationPo(inspiration)) != 1) {
            throw storage("写入灵感失败");
        }
    }

    @Override
    public void saveInspiration(Inspiration previous, Inspiration current) {
        if (database.updateInspiration(mapping.toInspirationPo(current), previous.revision()) != 1) {
            throw conflict("灵感已在其他页面更新，请刷新后重试");
        }
    }

    @Override
    public List<WorkCollection> findWorks() {
        return database.selectWorks().stream().map(row -> mapping.toWork(row, workItems(row.getId()))).toList();
    }

    @Override
    public Optional<WorkCollection> findWorkBySlug(String slug) {
        return Optional.ofNullable(database.selectWork(slug))
                .map(row -> mapping.toWork(row, workItems(row.getId())));
    }

    @Override
    public void addWork(WorkCollection work) {
        try {
            if (database.insertWork(mapping.toWorkPo(work)) != 1) throw storage("写入作品集失败");
            insertWorkItems(work);
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.SLUG_CONFLICT, "作品集 slug 已存在：" + work.slug(), exception);
        }
    }

    @Override
    public void saveWork(WorkCollection previous, WorkCollection current) {
        if (database.updateWork(mapping.toWorkPo(current), previous.revision()) != 1) {
            throw conflict("作品集已在其他页面更新，请刷新后重试");
        }
        database.deleteWorkItems(current.id());
        insertWorkItems(current);
    }

    @Override
    public List<ShareGrant> findShares(CreativeContentType contentType, String contentSlug) {
        return database.selectShares(contentType.name(), contentSlug).stream()
                .map(mapping::toShareGrant).toList();
    }

    @Override
    public Optional<ShareGrant> findShareById(String id) {
        return Optional.ofNullable(database.selectShareById(id)).map(mapping::toShareGrant);
    }

    @Override
    public Optional<ShareGrant> findShareByTokenHash(String tokenHash) {
        return Optional.ofNullable(database.selectShareByTokenHash(tokenHash)).map(mapping::toShareGrant);
    }

    @Override
    public void addShare(ShareGrant grant) {
        try {
            if (database.insertShare(mapping.toShareGrantPo(grant)) != 1) throw storage("创建分享链接失败");
        } catch (DataIntegrityViolationException exception) {
            throw new BlogException(BlogErrorCode.STORAGE_ERROR, "创建分享链接失败，请重试", exception);
        }
    }

    @Override
    public void saveShare(ShareGrant previous, ShareGrant current) {
        if (previous.revokedAt() != null || current.revokedAt() == null) return;
        if (database.revokeShare(current.id(), current.revokedAt()) != 1) {
            throw conflict("分享链接已被其他操作更新");
        }
    }

    @Override
    public void touchShare(String id, Instant accessedAt) {
        database.touchShare(id, accessedAt);
    }

    @Override
    public List<EditorialReview> findEditorialReviews(String postId) {
        return database.selectEditorialReviews(postId).stream().map(mapping::toEditorialReview).toList();
    }

    @Override
    public void addEditorialReview(EditorialReview review) {
        if (database.insertEditorialReview(mapping.toEditorialReviewPo(review)) != 1) {
            throw storage("保存 AI 编辑意见失败");
        }
    }

    @Override
    public Optional<DiscussionDigest> findDiscussionDigest(String postId) {
        return Optional.ofNullable(database.selectDiscussionDigest(postId)).map(mapping::toDiscussionDigest);
    }

    @Override
    public void saveDiscussionDigest(DiscussionDigest digest) {
        if (database.upsertDiscussionDigest(mapping.toDiscussionDigestPo(digest)) != 1) {
            throw storage("保存圆桌摘要失败");
        }
    }

    private List<WorkItem> workItems(String collectionId) {
        return database.selectWorkItems(collectionId).stream().map(mapping::toWorkItem).toList();
    }

    private void insertWorkItems(WorkCollection work) {
        if (work.items().isEmpty()) return;
        List<BlogWorkItemPo> items = work.items().stream()
                .map(item -> mapping.toWorkItemPo(item, work.id())).toList();
        database.insertWorkItems(items);
    }

    private static BlogException conflict(String message) {
        return new BlogException(BlogErrorCode.VERSION_CONFLICT, message);
    }

    private static BlogException storage(String message) {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, message);
    }
}
