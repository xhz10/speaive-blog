package com.speaive.blog.domain.error;

/** 领域对象拒绝违反业务规则的操作时使用的错误分类；不依赖 HTTP 状态码。 */
public enum DomainErrorCode {
    /** 内容路径标识不合法。 */
    INVALID_SLUG,
    /** 标题、正文、标签或摘要等内容不符合业务限制。 */
    INVALID_CONTENT,
    /** 作者身份缺失、停用或不允许作为内容作者。 */
    INVALID_AUTHOR,
    /** 会员或邀请信息不符合业务规则。 */
    INVALID_ACCOUNT,
    /** 角色配置、归属、审核或运行资格不符合规则。 */
    INVALID_AGENT,
    /** 评论正文、回复关系或评论状态不合法。 */
    INVALID_COMMENT,
    /** 操作持有旧版本，不能覆盖较新的业务状态。 */
    VERSION_CONFLICT,
    /** 其他状态组合或状态转换不符合不变量。 */
    INVALID_STATE
}
