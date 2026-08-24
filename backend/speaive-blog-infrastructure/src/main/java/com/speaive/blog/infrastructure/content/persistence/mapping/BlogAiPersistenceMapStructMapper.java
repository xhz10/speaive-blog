package com.speaive.blog.infrastructure.content.persistence.mapping;

import com.speaive.blog.domain.agent.AgentProfile;
import com.speaive.blog.domain.agent.AgentRun;
import com.speaive.blog.domain.agent.AgentRunStatus;
import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.comment.Comment;
import com.speaive.blog.domain.comment.CommentStatus;
import com.speaive.blog.infrastructure.content.persistence.po.AgentRunStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.AuthorStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.AuthorTypePo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAgentPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAgentRunPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogCommentPo;
import com.speaive.blog.infrastructure.content.persistence.po.CommentStatusPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface BlogAiPersistenceMapStructMapper {
    @Mapping(target = "id", source = "id")
    BlogAgentPo toAgentPo(AgentProfile agent);

    BlogAuthorPo toAuthorPo(Author author);

    Author toAuthor(BlogAuthorPo author);

    @Mapping(target = "authorId", source = "author.id")
    BlogCommentPo toCommentPo(Comment comment);

    BlogAgentRunPo toRunPo(AgentRun run);

    CommentStatusPo toPo(CommentStatus status);

    CommentStatus toDomain(CommentStatusPo status);

    AgentRunStatusPo toPo(AgentRunStatus status);

    AgentRunStatus toDomain(AgentRunStatusPo status);

    AuthorTypePo toPo(AuthorType type);

    AuthorType toDomain(AuthorTypePo type);

    AuthorStatusPo toPo(AuthorStatus status);

    AuthorStatus toDomain(AuthorStatusPo status);
}
