package com.speaive.blog.domain.analytics;

/** 浏览设备值对象；未知字段为空字符串，展示层负责显示“未提供”。 */
public record VisitorDevice(DeviceType type, String model, String operatingSystem, String browser) {}
