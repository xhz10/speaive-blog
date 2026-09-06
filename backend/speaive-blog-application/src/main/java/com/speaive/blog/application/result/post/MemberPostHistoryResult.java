package com.speaive.blog.application.result.post;

import java.time.Instant;
import java.util.List;
/** 仅作者本人可读的历史目录，正文通过本人文章读取或恢复后查看。 */
public record MemberPostHistoryResult(List<Item> items) {
    /** revision 是历史序号，version 用于当前文章的并发校验。 */
    public record Item(long revision, String title, Instant updatedAt, String status) {}
}
