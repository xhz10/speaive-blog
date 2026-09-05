package com.speaive.blog.domain.creative;

/** 灵感的处理进度；只有转化完成时才保留目标文章或小说片段的关联。 */
public enum InspirationStatus {
    /** 收件箱：刚记录，尚未进一步处理。 */
    INBOX,
    /** 酝酿中：正在发展成更完整的内容。 */
    DEVELOPING,
    /** 已转化：必须关联目标内容类型和 slug。 */
    CONVERTED,
    /** 已归档：暂时不继续处理，记录仍保留。 */
    ARCHIVED
}
