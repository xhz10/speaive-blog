package com.speaive.blog.application.service;

import com.speaive.blog.application.command.account.ChangePasswordCommand;
import com.speaive.blog.application.command.account.ResetPasswordCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.account.PasswordManagementUseCase;
import com.speaive.blog.application.port.out.persistence.AccountRepository;
import com.speaive.blog.application.port.out.persistence.PasswordResetRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.account.PasswordResetResult;
import com.speaive.blog.domain.account.MemberAccount;
import com.speaive.blog.domain.account.PasswordReset;
import com.speaive.blog.domain.error.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Supplier;

/** 密码用例保持账号锁在前、令牌锁在后的固定顺序；密码与令牌状态同事务提交。 */
public final class PasswordManagementApplicationService implements PasswordManagementUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration VALIDITY = Duration.ofMinutes(30);
    private static final String INVALID_LINK = "找回链接无效或已过期，请联系站长重新生成";
    private final AccountRepository accounts;
    private final PasswordResetRepository resets;
    private final TransactionRunner transactions;
    private final Clock clock;

    public PasswordManagementApplicationService(AccountRepository accounts, PasswordResetRepository resets,
            TransactionRunner transactions, Clock clock) {
        this.accounts = accounts; this.resets = resets; this.transactions = transactions; this.clock = clock;
    }

    @Override
    public void changePassword(String username, ChangePasswordCommand command) {
        domainErrors(() -> transactions.required(() -> {
            MemberAccount account = accounts.lockByUsername(normalize(username))
                    .orElseThrow(() -> invalid("账号不可用，请重新登录"));
            var now = clock.instant();
            MemberAccount changed = account.changePassword(command.newPasswordHash(), command.verifiedCredentialVersion(), now);
            accounts.savePassword(changed, account.credentialVersion());
            resets.revokeForAccount(account.id(), now);
            return null;
        }));
    }

    @Override
    public PasswordResetResult issueReset(String username) {
        return domainErrors(() -> transactions.required(() -> {
            MemberAccount account = accounts.lockByUsername(normalize(username))
                    .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "会员账号不存在"));
            if (!account.enabled()) throw invalid("账号已停用，不能生成找回链接");
            var now = clock.instant();
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            PasswordReset reset = new PasswordReset(hash(token), account.id(), account.credentialVersion(),
                    now.plus(VALIDITY), now, null);
            resets.revokeForAccount(account.id(), now);
            resets.add(reset);
            return new PasswordResetResult(account.identity().username(), token, reset.expiresAt());
        }));
    }

    @Override
    public void resetPassword(ResetPasswordCommand command) {
        domainErrors(() -> transactions.required(() -> {
            if (command == null || command.token() == null || !command.token().matches("[A-Za-z0-9_-]{43}")) throw invalid(INVALID_LINK);
            String tokenHash = hash(command.token());
            // 首次读取只用于确定锁账号的稳定 ID，不据此判断令牌是否可消费。
            PasswordReset candidate = resets.findByTokenHash(tokenHash).orElseThrow(() -> invalid(INVALID_LINK));
            MemberAccount account = accounts.lockById(candidate.accountId()).orElseThrow(() -> invalid(INVALID_LINK));
            PasswordReset reset = resets.lockByTokenHash(tokenHash).orElseThrow(() -> invalid(INVALID_LINK));
            var now = clock.instant();
            PasswordReset consumed = reset.consume(account, now);
            MemberAccount changed = account.changePassword(command.newPasswordHash(), reset.credentialVersion(), now);
            accounts.savePassword(changed, account.credentialVersion());
            resets.consume(consumed);
            resets.revokeForAccount(account.id(), now);
            return null;
        }));
    }

    @Override
    public boolean credentialsCurrent(String username, long version) {
        return transactions.required(() -> accounts.findByUsername(normalize(username))
                .map(account -> account.enabled() && account.credentialVersion() == version).orElse(false));
    }

    private static String normalize(String username) { return username == null ? "" : username.trim().toLowerCase(Locale.ROOT); }
    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("运行环境缺少 SHA-256", exception); }
    }
    private static BlogException invalid(String message) { return new BlogException(BlogErrorCode.INVALID_REQUEST, message); }
    private static <T> T domainErrors(Supplier<T> action) {
        try { return action.get(); }
        catch (DomainException exception) {
            if (exception.code() == com.speaive.blog.domain.error.DomainErrorCode.VERSION_CONFLICT)
                throw new BlogException(BlogErrorCode.VERSION_CONFLICT, exception.getMessage());
            throw invalid(exception.getMessage());
        }
    }
}
