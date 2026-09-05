package com.speaive.blog.application.port.out.transaction;

import java.util.function.Supplier;

/**
 * 数据库事务出站端口，使应用用例能声明事务范围而不依赖 Spring。实现负责提交、回滚及加入现有事务，不能把远程 AI 请求当成可回滚资源。
 */
public interface TransactionRunner {
    <T> T required(Supplier<T> action);
}
