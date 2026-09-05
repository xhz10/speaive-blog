package com.speaive.blog.infrastructure.content.persistence.mapping;

import com.speaive.blog.domain.creative.ContentRevision;
import com.speaive.blog.domain.creative.CreativeContentType;
import com.speaive.blog.domain.creative.DiscussionDigest;
import com.speaive.blog.domain.creative.EditorialReview;
import com.speaive.blog.domain.creative.Inspiration;
import com.speaive.blog.domain.creative.ShareGrant;
import com.speaive.blog.domain.creative.WorkCollection;
import com.speaive.blog.domain.creative.WorkItem;
import com.speaive.blog.infrastructure.content.persistence.po.BlogContentRevisionPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogDiscussionDigestPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogEditorialReviewPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogInspirationPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogShareGrantPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogWorkCollectionPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogWorkItemPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
/**
 * 创作工作区领域对象与数据库行的结构映射；不在映射表达式中决定可见性或权限。
 */
public interface CreativePersistenceMapStructMapper {
    Inspiration toInspiration(BlogInspirationPo value);
    BlogInspirationPo toInspirationPo(Inspiration value);

    @Mapping(target = "items", source = "items")
    WorkCollection toWork(BlogWorkCollectionPo value, List<WorkItem> items);

    BlogWorkCollectionPo toWorkPo(WorkCollection value);

    @Mapping(target = "position", source = "itemOrder")
    WorkItem toWorkItem(BlogWorkItemPo value);

    @Mapping(target = "collectionId", source = "collectionId")
    @Mapping(target = "itemOrder", source = "value.position")
    BlogWorkItemPo toWorkItemPo(WorkItem value, String collectionId);

    @Mapping(target = "revoke", ignore = true)
    ShareGrant toShareGrant(BlogShareGrantPo value);
    BlogShareGrantPo toShareGrantPo(ShareGrant value);

    EditorialReview toEditorialReview(BlogEditorialReviewPo value);
    BlogEditorialReviewPo toEditorialReviewPo(EditorialReview value);

    DiscussionDigest toDiscussionDigest(BlogDiscussionDigestPo value);
    BlogDiscussionDigestPo toDiscussionDigestPo(DiscussionDigest value);

    @Mapping(target = "contentType", source = "contentType")
    @Mapping(target = "tags", source = "tags")
    ContentRevision toContentRevision(
            BlogContentRevisionPo value,
            CreativeContentType contentType,
            List<String> tags);
}
