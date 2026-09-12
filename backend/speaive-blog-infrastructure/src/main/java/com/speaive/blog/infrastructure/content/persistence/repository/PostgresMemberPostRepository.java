package com.speaive.blog.infrastructure.content.persistence.repository;

import com.speaive.blog.application.error.*;
import com.speaive.blog.application.port.out.persistence.AccountRepository;
import com.speaive.blog.application.port.out.persistence.MemberPostRepository;
import com.speaive.blog.application.port.out.security.ContentEncryptionPort;
import com.speaive.blog.domain.post.*;
import com.speaive.blog.infrastructure.content.persistence.mapper.MemberPostDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.MemberPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.po.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Optional;

/**
 * 持久化同一个 Post 聚合，不实现另一套文章状态机。用例持有账号行锁，主记录和历史快照共享事务。
 * 载荷含所有内容字段，加密前后都不生成独立的标题、标签或摘要副本。
 */
public final class PostgresMemberPostRepository implements MemberPostRepository {
    private final MemberPostDatabaseMapper database;
    private final MemberPersistenceMapStructMapper mapping;
    private final AccountRepository accounts;
    private final ContentEncryptionPort encryption;
    private final JsonMapper json = JsonMapper.builder().build();

    public PostgresMemberPostRepository(MemberPostDatabaseMapper database, MemberPersistenceMapStructMapper mapping,
            AccountRepository accounts, ContentEncryptionPort encryption) {
        this.database = database; this.mapping = mapping; this.accounts = accounts; this.encryption = encryption;
    }

    @Override
    public List<Post> list(String ownerId, boolean publishedOnly, int page, int pageSize) {
        return database.list(ownerId, publishedOnly, pageSize, (long) (page - 1) * pageSize).stream()
                .map(row -> read(row, false)).toList();
    }

    @Override
    public long count(String ownerId, boolean publishedOnly) { return database.count(ownerId, publishedOnly); }

    @Override
    public Optional<Post> find(String ownerId, String slug, boolean publishedOnly) {
        return Optional.ofNullable(database.find(ownerId, slug, publishedOnly)).map(row -> read(row, false));
    }

    @Override
    public void add(Post post, boolean encrypted) {
        if (database.insert(stored(post, encrypted, false)) != 1) throw storage();
        if (database.insertRevision(stored(post, encrypted, true), PostRevisionEventType.CREATE.name()) != 1) throw storage();
    }

    @Override
    public void save(PostChange change, boolean encrypted) {
        Post post = change.current();
        MemberPostPo current = stored(post, encrypted, false);
        int changed = change.previous().archived() ? database.recover(current, change.expectedRevision())
                : post.archived() ? database.delete(current, change.expectedRevision())
                : database.update(current, change.expectedRevision());
        if (changed != 1) throw new BlogException(BlogErrorCode.VERSION_CONFLICT, "文章已更新，请刷新后重试");
        if (database.insertRevision(stored(post, encrypted, true), change.eventType().name()) != 1) throw storage();
    }

    @Override
    public List<Post> revisions(String ownerId, String postId) {
        return database.revisions(ownerId, postId).stream().map(row -> read(row, true)).toList();
    }

    @Override
    public Optional<Post> revision(String ownerId, String postId, long revision) {
        return Optional.ofNullable(database.revision(ownerId, postId, revision)).map(row -> read(row, true));
    }

    @Override
    public List<Post> listFiltered(String ownerId, MemberPostFilter filter, int page, int pageSize) {
        boolean archived = filter == MemberPostFilter.ARCHIVED;
        long offset = (long) (page - 1) * pageSize;
        var rows = archived ? database.archivedList(ownerId, pageSize, offset)
                : database.filtered(ownerId, filter.name(), pageSize, offset);
        return rows.stream().map(row -> read(row, archived)).toList();
    }

    @Override
    public long countFiltered(String ownerId, MemberPostFilter filter) {
        return filter == MemberPostFilter.ARCHIVED ? database.archivedCount(ownerId) : database.filteredCount(ownerId, filter.name());
    }

    @Override
    public Optional<Post> archived(String ownerId, String slug) {
        return Optional.ofNullable(database.archived(ownerId, slug)).map(row -> read(row, true));
    }

    @Override
    public List<Post> community(int page, int pageSize) {
        return database.community(pageSize, (long) (page - 1) * pageSize).stream().map(row -> read(row, false)).toList();
    }

    @Override
    public long communityCount() { return database.communityCount(); }

    @Override
    public void changeProtection(String ownerId, boolean encrypted) {
        // 账号锁使翻页期间的记录集合稳定；不按显示用的修订数量上限截断转换。
        protectAll(ownerId, encrypted, false);
        protectAll(ownerId, encrypted, true);
    }

    private void protectAll(String ownerId, boolean encrypted, boolean historical) {
        for (long offset = 0; ; offset += 100) {
            List<MemberPostPo> rows = historical ? database.protectionRevisions(ownerId, offset)
                    : database.protectionPosts(ownerId, offset);
            for (MemberPostPo row : rows) {
                String plaintext = plaintext(row, historical);
                String payload = encrypted ? encryption.encrypt(context(row, historical), plaintext) : plaintext;
                int changed = historical ? database.protectRevision(row, payload, encrypted)
                        : database.protectPost(row, payload, encrypted);
                if (changed != 1) throw storage();
            }
            if (rows.size() < 100) return;
        }
    }

    private MemberPostPo stored(Post post, boolean encrypted, boolean historical) {
        String plaintext;
        try { plaintext = json.writeValueAsString(mapping.content(post.content())); }
        catch (RuntimeException exception) { throw storage(); }
        MemberPostPo row = mapping.stored(post.snapshot(), plaintext, encrypted);
        return encrypted ? mapping.stored(post.snapshot(), encryption.encrypt(context(row, historical), plaintext), true) : row;
    }

    private Post read(MemberPostPo row, boolean historical) {
        String plaintext = plaintext(row, historical);
        var owner = accounts.findById(row.ownerId()).orElseThrow(PostgresMemberPostRepository::storage);
        try {
            var content = mapping.content(json.readValue(plaintext, MemberPostContentPo.class));
            return Post.rehydrate(mapping.snapshot(row, content, owner.identity()));
        } catch (RuntimeException exception) { throw storage(); }
    }

    private String plaintext(MemberPostPo row, boolean historical) {
        return row.payloadEncrypted() ? encryption.decrypt(context(row, historical), row.payload()) : row.payload();
    }

    private static String context(MemberPostPo row, boolean historical) {
        // 绑定记录和访问元数据：替换作者、公开状态、修订号或调换历史密文都会使认证失败。
        return String.join("|", historical ? "revision" : "current", row.ownerId(), row.id(), row.slug(),
                Long.toString(row.revision()), row.status(), row.visibility(), Boolean.toString(row.archived()));
    }

    private static BlogException storage() {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, "会员内容存储异常，请联系站长检查数据完整性");
    }
}
