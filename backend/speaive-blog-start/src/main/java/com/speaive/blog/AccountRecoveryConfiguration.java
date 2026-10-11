package com.speaive.blog;

import com.speaive.blog.application.port.in.account.PasswordManagementUseCase;
import com.speaive.blog.application.port.out.persistence.*;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.service.PasswordManagementApplicationService;
import com.speaive.blog.infrastructure.content.persistence.mapper.PasswordResetDatabaseMapper;
import com.speaive.blog.infrastructure.content.persistence.mapping.AccountRecoveryPersistenceMapStructMapper;
import com.speaive.blog.infrastructure.content.persistence.repository.PostgresPasswordResetRepository;
import org.springframework.context.annotation.*;
import java.time.Clock;

/** 账号找回组合根，控制器只依赖用例契约。 */
@Configuration(proxyBeanMethods = false)
public class AccountRecoveryConfiguration {
    @Bean PasswordResetRepository passwordResetRepository(PasswordResetDatabaseMapper database, AccountRecoveryPersistenceMapStructMapper mapping) {
        return new PostgresPasswordResetRepository(database, mapping);
    }
    @Bean PasswordManagementUseCase passwordManagementUseCase(AccountRepository accounts, PasswordResetRepository resets, TransactionRunner transactions) {
        return new PasswordManagementApplicationService(accounts, resets, transactions, Clock.systemUTC());
    }
}
