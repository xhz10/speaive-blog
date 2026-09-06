package com.speaive.blog.domain.account;

/** 会员业务身份，独立于 Spring 的登录角色；管理员身份继续由站长认证配置管理。 */
public enum MemberRole {
    /** 普通会员，可使用既有会员功能，没有文章写作权限。 */
    READER,
    /** 作者，可编辑本人文章；是否可以公开发布还需检查 canPublish。 */
    WRITER
}
