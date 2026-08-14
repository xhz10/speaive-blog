package com.speaive.blog.application.port.out.transaction;

import java.util.function.Supplier;

public interface TransactionRunner {
    <T> T required(Supplier<T> action);
}
