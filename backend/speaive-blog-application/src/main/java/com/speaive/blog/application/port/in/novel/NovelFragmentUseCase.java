package com.speaive.blog.application.port.in.novel;

import com.speaive.blog.application.command.novel.NovelFragmentWriteCommand;
import com.speaive.blog.application.result.novel.NovelFragmentDetailResult;
import com.speaive.blog.application.result.novel.NovelFragmentListResult;

public interface NovelFragmentUseCase {
    NovelFragmentListResult listStudioFragments();

    NovelFragmentListResult listPublishedFragments();

    NovelFragmentDetailResult getStudioFragment(String slug);

    NovelFragmentDetailResult getPublishedFragment(String slug);

    NovelFragmentDetailResult createDraft(NovelFragmentWriteCommand command);

    NovelFragmentDetailResult update(String slug, String version, NovelFragmentWriteCommand command);

    NovelFragmentDetailResult publish(String slug, String version);

    NovelFragmentDetailResult unpublish(String slug, String version);
}
