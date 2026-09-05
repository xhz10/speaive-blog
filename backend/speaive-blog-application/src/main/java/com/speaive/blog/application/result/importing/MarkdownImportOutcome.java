package com.speaive.blog.application.result.importing;

/** Markdown 投递箱的一次导入结果，用于区分新写入与按内容哈希幂等命中。 */
public enum MarkdownImportOutcome {
    /** 已导入：本次创建了草稿并记录导入台账。 */
    IMPORTED,
    /** 已处理过：相同内容哈希已有记录，本次不重复创建文章。 */
    ALREADY_IMPORTED
}
