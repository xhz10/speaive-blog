package com.speaive.blog.application.result.post;
import java.util.List;

/** 朋友公开作品的分页结果，不包含会员设置、私密数量或归档信息。 */
public record CommunityPostListResult(long total, int page, int pageSize, List<PostSummaryResult> items) {
    public CommunityPostListResult { items = List.copyOf(items); }
}
