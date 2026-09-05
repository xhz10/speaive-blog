package com.speaive.blog.application.service;

import com.speaive.blog.application.command.account.CreateInvitationCommand;
import com.speaive.blog.application.command.account.RegisterMemberCommand;
import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.in.account.MemberAccountUseCase;
import com.speaive.blog.application.port.out.persistence.AccountRepository;
import com.speaive.blog.application.port.out.persistence.InvitationRepository;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.account.AccountCredentialsResult;
import com.speaive.blog.application.result.account.InvitationListResult;
import com.speaive.blog.application.result.account.InvitationResult;
import com.speaive.blog.application.result.account.MemberResult;
import com.speaive.blog.domain.account.Invitation;
import com.speaive.blog.domain.account.MemberAccount;
import com.speaive.blog.domain.error.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 邀请码会员注册与账号查询用例，协调邀请配额、身份和密码哈希的保存。HTTP 登录会话由入站安全层负责。
 */
public final class MemberAccountApplicationService implements MemberAccountUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final InvitationRepository invitations;
    private final TransactionRunner transactions;
    private final Clock clock;

    public MemberAccountApplicationService(
            AccountRepository accounts,
            InvitationRepository invitations,
            TransactionRunner transactions,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.invitations = Objects.requireNonNull(invitations, "invitations");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public MemberResult register(RegisterMemberCommand command) {
        if (command == null) {
            throw invalid("注册信息不能为空");
        }
        return withDomainErrors(() -> transactions.required(() -> {
            String username = normalizeUsername(command.username());
            if (accounts.findByUsername(username).isPresent()) {
                throw invalid("用户名已经存在");
            }
            Instant now = clock.instant();
            String codeHash = hashInvitationCode(command.invitationCode());
            Invitation invitation = invitations.lockByCodeHash(codeHash)
                    .orElseThrow(() -> invalid("邀请码无效、已用完或已经过期"));
            Invitation consumed = invitation.consume(now);
            MemberAccount account = MemberAccount.register(
                    UUID.randomUUID().toString(), username, command.displayName(), command.passwordHash(), now);
            accounts.add(account);
            invitations.save(consumed, invitation.usedCount());
            return result(account);
        }));
    }

    @Override
    public MemberResult getMember(String username) {
        return withDomainErrors(() -> transactions.required(() -> result(requiredAccount(username))));
    }

    @Override
    public Optional<AccountCredentialsResult> findCredentials(String username) {
        return withDomainErrors(() -> transactions.required(() -> accounts.findByUsername(normalizeUsername(username))
                .map(account -> new AccountCredentialsResult(
                        account.identity().username(), account.passwordHash(), account.enabled()))));
    }

    @Override
    public InvitationResult createInvitation(CreateInvitationCommand command) {
        if (command == null || command.validDays() < 1 || command.validDays() > 365
                || command.maxUses() < 1 || command.maxUses() > 100) {
            throw invalid("邀请码有效期必须为 1 到 365 天，使用次数必须为 1 到 100 次");
        }
        return withDomainErrors(() -> transactions.required(() -> {
            Instant now = clock.instant();
            String code = generateInvitationCode();
            Invitation invitation = Invitation.create(
                    UUID.randomUUID().toString(), hashInvitationCode(code), command.maxUses(),
                    now.plus(Duration.ofDays(command.validDays())), now);
            invitations.add(invitation);
            return result(invitation, code);
        }));
    }

    @Override
    public InvitationListResult listInvitations() {
        return withDomainErrors(() -> transactions.required(() -> new InvitationListResult(
                invitations.findAll().stream().map(invitation -> result(invitation, null)).toList())));
    }

    private MemberAccount requiredAccount(String username) {
        return accounts.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new BlogException(BlogErrorCode.NOT_FOUND, "会员账号不存在"));
    }

    private static MemberResult result(MemberAccount account) {
        return new MemberResult(
                account.id(), account.identity().username(), account.identity().displayName(),
                account.enabled(), account.createdAt());
    }

    private static InvitationResult result(Invitation invitation, String code) {
        return new InvitationResult(
                invitation.id(), code, invitation.maxUses(), invitation.usedCount(),
                invitation.expiresAt(), invitation.createdAt());
    }

    private static String generateInvitationCode() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return "spv_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashInvitationCode(String code) {
        String normalized = code == null ? "" : code.trim();
        if (normalized.length() < 12 || normalized.length() > 100) {
            throw invalid("邀请码无效、已用完或已经过期");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private static String normalizeUsername(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static BlogException invalid(String message) {
        return new BlogException(BlogErrorCode.INVALID_REQUEST, message);
    }

    private static <T> T withDomainErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (DomainException exception) {
            throw invalid(exception.getMessage());
        }
    }
}
