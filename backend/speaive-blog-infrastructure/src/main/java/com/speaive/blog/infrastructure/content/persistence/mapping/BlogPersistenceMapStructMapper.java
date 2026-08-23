package com.speaive.blog.infrastructure.content.persistence.mapping;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.post.PostContent;
import com.speaive.blog.domain.post.PostRevisionEventType;
import com.speaive.blog.domain.post.PostSlug;
import com.speaive.blog.domain.post.PostSnapshot;
import com.speaive.blog.domain.post.PostStatus;
import com.speaive.blog.domain.post.PostSummary;
import com.speaive.blog.domain.post.PostVisibility;
import com.speaive.blog.infrastructure.content.persistence.po.AuthorStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.AuthorTypePo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogPostPo;
import com.speaive.blog.infrastructure.content.persistence.po.PostStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.PostVisibilityPo;
import com.speaive.blog.infrastructure.content.persistence.po.RevisionEventTypePo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface BlogPersistenceMapStructMapper {
    @Mapping(target = "content", expression = "java(toContent(post, tags))")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "id", source = "post.id")
    @Mapping(target = "status", source = "post.status")
    @Mapping(target = "archived", constant = "false")
    PostSnapshot toSnapshot(BlogPostPo post, List<String> tags, BlogAuthorPo author);

    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "id", source = "post.id")
    @Mapping(target = "status", source = "post.status")
    PostSummary toSummary(BlogPostPo post, List<String> tags, BlogAuthorPo author);

    @Mapping(target = "tags", source = "tags")
    PostContent toContent(BlogPostPo post, List<String> tags);

    Author toAuthor(BlogAuthorPo author);

    BlogAuthorPo toAuthorPo(Author author);

    @Mapping(target = "slug", source = "slug.value")
    @Mapping(target = "title", source = "content.title")
    @Mapping(target = "description", source = "content.description")
    @Mapping(target = "publishedAt", source = "content.publishedAt")
    @Mapping(target = "body", source = "content.body")
    @Mapping(target = "cover", source = "content.cover")
    @Mapping(target = "authorId", source = "author.id")
    BlogPostPo toPostPo(PostSnapshot snapshot);

    PostStatus toDomain(PostStatusPo status);

    PostStatusPo toPo(PostStatus status);

    PostVisibility toDomain(PostVisibilityPo visibility);

    PostVisibilityPo toPo(PostVisibility visibility);

    AuthorType toDomain(AuthorTypePo type);

    AuthorTypePo toPo(AuthorType type);

    AuthorStatus toDomain(AuthorStatusPo status);

    AuthorStatusPo toPo(AuthorStatus status);

    RevisionEventTypePo toPo(PostRevisionEventType eventType);

    default PostSlug toPostSlug(String value) {
        return PostSlug.of(value);
    }
}
