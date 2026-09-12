package com.speaive.blog.application.command.post;

/** 预览当前输入；仅用于本次渲染，不生成文章、修订或浏览器草稿副本。 */
public record PreviewWritingCommand(String title, String body) { }
