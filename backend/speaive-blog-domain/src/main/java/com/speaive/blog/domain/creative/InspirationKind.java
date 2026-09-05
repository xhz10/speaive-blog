package com.speaive.blog.domain.creative;

/** 灵感的内容分类，只描述记录的性质，不决定处理进度。 */
public enum InspirationKind {
    /** 想法：待展开的主题、观点或构思。 */
    IDEA,
    /** 场景：环境、动作或事件片段。 */
    SCENE,
    /** 对白：人物说话的句子或对话片段。 */
    DIALOGUE,
    /** 人物：性格、关系或角色设定。 */
    CHARACTER,
    /** 问题：需要继续追问或求证的疑问。 */
    QUESTION
}
