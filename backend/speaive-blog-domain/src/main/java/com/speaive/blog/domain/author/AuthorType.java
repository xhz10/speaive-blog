package com.speaive.blog.domain.author;

/** 内容署名的身份类型；它与登录凭证及 HTTP 授权角色是不同概念。 */
public enum AuthorType {
    /** 真人作者身份，可对应站长或会员；不表示必然拥有管理员权限。 */
    HUMAN,
    /** AI 角色的署名身份，由 AgentProfile 管理提示词与运行资格。 */
    AGENT,
    /** 系统身份：保留给系统行为；具体业务入口另外决定是否允许该类型。 */
    SYSTEM
}
