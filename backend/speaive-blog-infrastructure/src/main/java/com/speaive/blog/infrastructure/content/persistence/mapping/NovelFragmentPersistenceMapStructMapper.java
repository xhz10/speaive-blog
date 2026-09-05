package com.speaive.blog.infrastructure.content.persistence.mapping;

import com.speaive.blog.domain.author.Author;
import com.speaive.blog.domain.author.AuthorStatus;
import com.speaive.blog.domain.author.AuthorType;
import com.speaive.blog.domain.novel.NovelFragmentContent;
import com.speaive.blog.domain.novel.NovelFragmentRevisionEventType;
import com.speaive.blog.domain.novel.NovelFragmentSlug;
import com.speaive.blog.domain.novel.NovelFragmentSnapshot;
import com.speaive.blog.domain.novel.NovelFragmentStatus;
import com.speaive.blog.domain.novel.NovelFragmentSummary;
import com.speaive.blog.domain.novel.NovelFragmentVisibility;
import com.speaive.blog.infrastructure.content.persistence.po.AuthorStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.AuthorTypePo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogAuthorPo;
import com.speaive.blog.infrastructure.content.persistence.po.BlogNovelFragmentPo;
import com.speaive.blog.infrastructure.content.persistence.po.NovelFragmentRevisionEventTypePo;
import com.speaive.blog.infrastructure.content.persistence.po.NovelFragmentStatusPo;
import com.speaive.blog.infrastructure.content.persistence.po.NovelFragmentVisibilityPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
/**
 * 小说片段及其修订历史的持久化边界映射。
 */
public interface NovelFragmentPersistenceMapStructMapper {
    @Mapping(target = "content", expression = "java(toContent(fragment))")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "id", source = "fragment.id")
    @Mapping(target = "status", source = "fragment.status")
    NovelFragmentSnapshot toSnapshot(BlogNovelFragmentPo fragment, BlogAuthorPo author);

    @Mapping(target = "content", expression = "java(toContent(fragment))")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "id", source = "fragment.id")
    @Mapping(target = "status", source = "fragment.status")
    NovelFragmentSummary toSummary(BlogNovelFragmentPo fragment, BlogAuthorPo author);

    default NovelFragmentContent toContent(BlogNovelFragmentPo fragment) {
        return new NovelFragmentContent(fragment.getTitle(), fragment.getExcerpt(), fragment.getBody());
    }

    Author toAuthor(BlogAuthorPo author);

    @Mapping(target = "slug", source = "slug.value")
    @Mapping(target = "title", source = "content.title")
    @Mapping(target = "excerpt", source = "content.excerpt")
    @Mapping(target = "body", source = "content.body")
    @Mapping(target = "authorId", source = "author.id")
    BlogNovelFragmentPo toPo(NovelFragmentSnapshot snapshot);

    NovelFragmentStatus toDomain(NovelFragmentStatusPo status);

    NovelFragmentStatusPo toPo(NovelFragmentStatus status);

    NovelFragmentVisibility toDomain(NovelFragmentVisibilityPo visibility);

    NovelFragmentVisibilityPo toPo(NovelFragmentVisibility visibility);

    AuthorType toDomain(AuthorTypePo type);

    AuthorStatus toDomain(AuthorStatusPo status);

    NovelFragmentRevisionEventTypePo toPo(NovelFragmentRevisionEventType eventType);

    default NovelFragmentSlug toNovelFragmentSlug(String value) {
        return NovelFragmentSlug.of(value);
    }
}
