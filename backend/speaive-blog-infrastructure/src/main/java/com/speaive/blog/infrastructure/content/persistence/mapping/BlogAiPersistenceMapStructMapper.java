package com.speaive.blog.infrastructure.content.persistence.mapping;

import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.agent.AgentReviewStatus;
import com.speaive.blog.domain.agent.AgentRun;
import com.speaive.blog.domain.agent.AgentRunStatus;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.comment.Comment;
import com.speaive.blog.domain.comment.CommentStatus;
import com.speaive.blog.domain.automation.CommunityCommentJob;
import com.speaive.blog.domain.automation.CommunityCommentJobStatus;
import com.speaive.blog.domain.automation.CommunityPostPolicy;
import com.speaive.blog.domain.post.PostAiSummary;
import com.speaive.blog.infrastructure.content.persistence.po.AgentRunStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.AgentReviewStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.AuthorStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.AuthorTypePo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAgentPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAgentRunPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogCommentPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogCommunityCommentJobPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogCommunityPostPolicyPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogPostAiSummaryPo;
import com.speaive.blog.infrastructure.content.persistence.po.CommentStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.CommunityCommentJobStatusPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
/**
 * AI 角色、运行审计、摘要和评论的持久化边界映射，显式转换领域枚举与 PO 枚举。
 */
public interface BlogAiPersistenceMapStructMapper {
    @Mapping(target = "id", source = "id")
    BlogAgentPo toAgentPo(AgentProfile agent);

    BlogAuthorPo toAuthorPo(Author author);

    Author toAuthor(BlogAuthorPo author);

    @Mapping(target = "authorId", source = "author.id")
    BlogCommentPo toCommentPo(Comment comment);

    BlogAgentRunPo toRunPo(AgentRun run);

    BlogPostAiSummaryPo toPostAiSummaryPo(PostAiSummary summary);

    PostAiSummary toPostAiSummary(BlogPostAiSummaryPo summary);

    BlogCommunityPostPolicyPo toCommunityPostPolicyPo(CommunityPostPolicy policy);

    CommunityPostPolicy toCommunityPostPolicy(BlogCommunityPostPolicyPo policy);

    BlogCommunityCommentJobPo toCommunityCommentJobPo(CommunityCommentJob job);

    @Mapping(target = "succeed", ignore = true)
    CommunityCommentJob toCommunityCommentJob(BlogCommunityCommentJobPo job);

    CommentStatusPo toPo(CommentStatus status);

    CommentStatus toDomain(CommentStatusPo status);

    AgentRunStatusPo toPo(AgentRunStatus status);

    AgentRunStatus toDomain(AgentRunStatusPo status);

    AuthorTypePo toPo(AuthorType type);

    AuthorType toDomain(AuthorTypePo type);

    AuthorStatusPo toPo(AuthorStatus status);

    AuthorStatus toDomain(AuthorStatusPo status);

    AgentReviewStatusPo toPo(AgentReviewStatus status);

    AgentReviewStatus toDomain(AgentReviewStatusPo status);

    CommunityCommentJobStatusPo toPo(CommunityCommentJobStatus status);

    CommunityCommentJobStatus toDomain(CommunityCommentJobStatusPo status);
}
