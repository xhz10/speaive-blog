package com.speaive.blog.application.result.post;

/** 未保存文字的预览结果；HTML 已经过统一 Markdown 清洗器处理。 */
public record WritingPreviewResult(String title, String html) { }
