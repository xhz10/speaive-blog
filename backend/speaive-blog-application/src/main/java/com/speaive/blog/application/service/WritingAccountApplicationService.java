package com.speaive.blog.application.service;

import com.speaive.blog.application.command.account.*;
import com.speaive.blog.application.error.*;
import com.speaive.blog.application.port.in.account.WritingAccountUseCase;
import com.speaive.blog.application.port.out.persistence.*;
import com.speaive.blog.application.port.out.security.ContentEncryptionPort;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.application.result.account.*;
import com.speaive.blog.domain.account.*;
import java.time.Clock;

/** 账号资格与存储偏好用例；同一账号锁串行化设置转换和文章写入，避免混合存储。 */
public final class WritingAccountApplicationService implements WritingAccountUseCase {
    private final AccountRepository accounts;
    private final MemberPostRepository posts;
    private final ContentEncryptionPort encryption;
    private final TransactionRunner transactions;
    private final Clock clock;

    public WritingAccountApplicationService(AccountRepository accounts, MemberPostRepository posts,
            ContentEncryptionPort encryption, TransactionRunner transactions, Clock clock) {
        this.accounts = accounts; this.posts = posts; this.encryption = encryption;
        this.transactions = transactions; this.clock = clock;
    }

    @Override
    public WritingAccountResult get(String authenticatedUsername) {
        return result(accounts.findByUsername(authenticatedUsername).filter(MemberAccount::enabled)
                .orElseThrow(WritingAccountApplicationService::notFound));
    }

    @Override
    public WritingAccountListResult listMembers() {
        return new WritingAccountListResult(accounts.findAll().stream().map(this::result).toList());
    }

    @Override
    public WritingAccountResult setPermissions(String username, MemberPermissionsCommand command) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            MemberAccount account = accounts.lockByUsername(username).orElseThrow(WritingAccountApplicationService::notFound);
            MemberRole role;
            try { role = MemberRole.valueOf(command.role()); }
            catch (IllegalArgumentException | NullPointerException exception) {
                throw new BlogException(BlogErrorCode.INVALID_REQUEST, "会员身份必须是 READER 或 WRITER");
            }
            MemberAccount changed = account.changeWritingPermissions(role, command.canPublish(),
                    command.encryptionAllowed(), command.version(), clock.instant());
            if (account.role() == MemberRole.WRITER && changed.role() == MemberRole.READER) {
                // 收回作者身份时撤回公开文章，防止以后重新授予身份时旧内容自动重新公开。
                while (true) {
                    var published = posts.list(account.id(), true, 1, 20);
                    if (published.isEmpty()) break;
                    for (var post : published) posts.save(post.unpublish(post.version(), clock.instant()), account.contentEncrypted());
                }
            }
            accounts.saveSettings(changed, account.settingsVersion());
            return result(changed);
        }));
    }

    @Override
    public WritingAccountResult setEncryption(String authenticatedUsername, ContentEncryptionCommand command) {
        return PostApplicationSupport.withDomainErrors(() -> transactions.required(() -> {
            MemberAccount account = accounts.lockByUsername(authenticatedUsername).filter(MemberAccount::enabled)
                    .orElseThrow(WritingAccountApplicationService::notFound);
            MemberAccount changed = account.chooseContentEncryption(command.encrypted(), command.version(), clock.instant());
            if (command.encrypted() && !encryption.available()) {
                throw new BlogException(BlogErrorCode.INVALID_REQUEST, "站长尚未配置内容密钥，暂时无法开启加密");
            }
            // 当前内容、包括归档在内的全部历史、账号开关任一步失败都回滚；不推进文章修订号。
            posts.changeProtection(account.id(), command.encrypted());
            accounts.saveSettings(changed, account.settingsVersion());
            return result(changed);
        }));
    }

    private WritingAccountResult result(MemberAccount account) {
        return new WritingAccountResult(account.id(), account.identity().username(), account.identity().displayName(),
                account.role().name(), account.canPublish(), account.encryptionAllowed(), account.contentEncrypted(),
                encryption.available(), account.settingsVersion());
    }

    private static BlogException notFound() { return new BlogException(BlogErrorCode.NOT_FOUND, "会员账号不存在或已停用"); }
}
