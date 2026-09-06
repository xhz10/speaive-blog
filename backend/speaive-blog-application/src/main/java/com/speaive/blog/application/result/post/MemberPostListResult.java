package com.speaive.blog.application.result.post;

import java.util.List;
/** 作者主页或工作台的分页列表；不把所有正文或私密文章总数暴露给公开主页。 */
public record MemberPostListResult(String username, String displayName, long total, int page, int pageSize,
        List<PostSummaryResult> items) {}
