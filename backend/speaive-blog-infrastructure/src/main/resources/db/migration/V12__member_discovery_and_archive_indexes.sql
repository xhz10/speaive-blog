-- 仅索引公开状态和归档元数据，不创建标题、正文或标签的明文副本。
CREATE INDEX member_post_public_updated ON blog_member_post(updated_at DESC, id)
    WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC';
CREATE INDEX member_revision_archived_updated ON blog_member_post_revision(owner_id, updated_at DESC, id)
    WHERE archived;
