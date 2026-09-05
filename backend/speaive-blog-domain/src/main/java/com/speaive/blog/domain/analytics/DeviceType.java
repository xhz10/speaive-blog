package com.speaive.blog.domain.analytics;

/** 访客设备类别，根据请求提供的信息尽力识别，不代表可靠的硬件指纹。 */
public enum DeviceType {
    /** 手机，包括浏览器明确报告的移动设备。 */
    MOBILE,
    /** 平板；某些以桌面模式运行的平板可能被识别为电脑。 */
    TABLET,
    /** 桌面或笔记本电脑，无法据此识别具体硬件型号。 */
    DESKTOP,
    /** 用户代理中明确声明的爬虫或自动化程序，默认不计入阅读统计。 */
    BOT,
    /** 信息缺失或无法识别。 */
    UNKNOWN
}
