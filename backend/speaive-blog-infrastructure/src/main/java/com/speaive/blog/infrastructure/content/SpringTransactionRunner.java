package com.speaive.blog.infrastructure.content;

import com.speaive.blog.application.port.out.TransactionRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

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
