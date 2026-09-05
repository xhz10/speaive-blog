package com.speaive.blog.infrastructure.transaction;

import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * TransactionRunner 的 Spring 适配器，使用 TransactionTemplate 执行 REQUIRED 语义。数据库事务异常向外传播，领域层不需要事务注解。
 */
public final class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate transactions;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this(new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager")));
    }

    SpringTransactionRunner(TransactionTemplate transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    @Override
    public <T> T required(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        return transactions.execute(status -> action.get());
    }
}
