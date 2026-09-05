package com.speaive.blog.interfaces.http.error;

/**
 * 统一 HTTP 错误体：code 供前端分支处理，message 供用户阅读。
 */
public record ApiError(String code, String message) {
}
