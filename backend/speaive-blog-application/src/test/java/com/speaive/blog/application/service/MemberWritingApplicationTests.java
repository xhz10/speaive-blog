package com.speaive.blog.application.service;

import com.speaive.blog.application.command.account.ContentEncryptionCommand;
import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.error.*;
import com.speaive.blog.application.port.out.persistence.*;
import com.speaive.blog.application.port.out.security.ContentEncryptionPort;
import com.speaive.blog.application.port.out.markdown.MarkdownPort;
import com.speaive.blog.application.port.out.transaction.TransactionRunner;
import com.speaive.blog.domain.account.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class MemberWritingApplicationTests {
    static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final TransactionRunner TX = new TransactionRunner() { public <T> T required(Supplier<T> action) { return action.get(); } };
    static MemberAccount writer() { return MemberAccount.register("member", "alice", "朋友", "hash", NOW)
            .changeWritingPermissions(MemberRole.WRITER, false, true, 1, NOW); }

    @Test
    void protectionMustFinishBeforeSettingsAreSavedAndFailureNeverSavesTheFlag() {
        List<String> events = new ArrayList<>();
        var accounts = fake(AccountRepository.class, (method, args) -> {
            events.add(method);
            if (method.equals("lockByUsername")) return Optional.of(writer());
            if (method.equals("saveSettings")) {
                assertTrue(((MemberAccount) args[0]).contentEncrypted()); assertEquals(2L, args[1]); return null;
            }
            throw new AssertionError(method);
        });
        var encryption = fake(ContentEncryptionPort.class, (method, args) -> true);
        var posts = fake(MemberPostRepository.class, (method, args) -> {
            events.add(method); assertEquals("member", args[0]); assertEquals(true, args[1]); return null;
        });
        new WritingAccountApplicationService(accounts, posts, encryption, TX, CLOCK).setEncryption("alice", new ContentEncryptionCommand(true, 2));
        assertEquals(List.of("lockByUsername", "changeProtection", "saveSettings"), events);
        events.clear();
        var broken = fake(MemberPostRepository.class, (method, args) -> { throw new BlogException(BlogErrorCode.STORAGE_ERROR, "转换失败"); });
        assertThrows(BlogException.class, () -> new WritingAccountApplicationService(accounts, broken, encryption, TX, CLOCK)
                .setEncryption("alice", new ContentEncryptionCommand(true, 2)));
        assertEquals(List.of("lockByUsername"), events);
    }

    @Test
    void missingKeysAndStaleAccountVersionsRejectBeforeRewritingAnyContent() {
        var accounts = fake(AccountRepository.class, (method, args) -> Optional.of(writer()));
        var posts = fake(MemberPostRepository.class, (method, args) -> { throw new AssertionError("不应该迁移任何记录"); });
        var encryption = fake(ContentEncryptionPort.class, (method, args) -> false);
        var service = new WritingAccountApplicationService(accounts, posts, encryption, TX, CLOCK);
        assertThrows(BlogException.class, () -> service.setEncryption("alice", new ContentEncryptionCommand(true, 2)));
        assertEquals(BlogErrorCode.VERSION_CONFLICT, assertThrows(BlogException.class,
                () -> service.setEncryption("alice", new ContentEncryptionCommand(true, 1))).code());
    }

    @Test
    void readerCannotCreateEvenWhenLoggedInAndRepositoriesNeverSeeUntrustedOwnerIds() {
        MemberAccount reader = MemberAccount.register("member", "alice", "朋友", "hash", NOW);
        var accounts = fake(AccountRepository.class, (method, args) -> { assertEquals("alice", args[0]); return Optional.of(reader); });
        var posts = fake(MemberPostRepository.class, (method, args) -> { throw new AssertionError("无写作资格不能调用文章仓储"); });
        var markdown = fake(MarkdownPort.class, (method, args) -> { throw new AssertionError("无写作资格不应处理正文"); });
        var service = new MemberWritingApplicationService(accounts, posts, markdown, TX, CLOCK);
        assertEquals(BlogErrorCode.FORBIDDEN, assertThrows(BlogException.class,
                () -> service.create("alice", new PostWriteCommand(null, "文章", "", NOW, List.of(), null, "正文"))).code());
        assertEquals(BlogErrorCode.NOT_FOUND, assertThrows(BlogException.class, () -> service.profile("alice", 1)).code());
    }

    private interface Call { Object run(String method, Object[] args); }
    private static <T> T fake(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> call.run(method.getName(), args == null ? new Object[0] : args)));
    }
}
