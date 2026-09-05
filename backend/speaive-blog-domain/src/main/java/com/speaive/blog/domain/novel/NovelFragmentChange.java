package com.speaive.blog.domain.novel;

/**
 * 小说片段变更前后的状态与修订原因，保证作者身份和修订推进规则不被仓储绕过。
 *
 * @param previous 执行本次操作前的聚合状态，提供并发比较依据
 * @param current 通过领域校验后的新聚合状态
 * @param eventType 产生该修订的业务操作原因
 */
public record NovelFragmentChange(
        NovelFragment previous,
        NovelFragment current,
        NovelFragmentRevisionEventType eventType
) {
    public long expectedRevision() {
        return previous.revision();
    }
}
