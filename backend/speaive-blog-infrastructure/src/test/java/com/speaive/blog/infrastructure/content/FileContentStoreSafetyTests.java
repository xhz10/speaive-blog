package com.speaive.blog.infrastructure.content;

import com.speaive.blog.application.BlogErrorCode;
import com.speaive.blog.application.BlogException;
import com.speaive.blog.application.PostWriteCommand;
import com.speaive.blog.domain.Post;
import com.speaive.blog.domain.PostStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileContentStoreSafetyTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-02T08:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void noReplaceCommitNeverOverwritesTargetCreatedInTheCommitWindow() throws Exception {
        Path data = temporaryDirectory.resolve("data");
        byte[] outsideContent = "external writer content".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean injected = new AtomicBoolean();
        FileContentStore store = store(data, (source, target, replaceExisting) -> {
            if (!replaceExisting && target.getFileName().toString().equals("race.md")
                    && injected.compareAndSet(false, true)) {
                write(target, outsideContent, StandardOpenOption.CREATE_NEW);
            }
        });

        BlogException exception = assertThrows(BlogException.class,
                () -> store.createDraft(command("race", "应用内容")));

        assertEquals(BlogErrorCode.SLUG_CONFLICT, exception.code());
        assertEquals("external writer content", Files.readString(data.resolve("drafts/race.md")));
    }

    @Test
    void updateRechecksVersionImmediatelyBeforeReplacingTheFile() throws Exception {
        Path data = temporaryDirectory.resolve("data");
        AtomicBoolean armed = new AtomicBoolean();
        FileContentStore store = store(data, (source, target, replaceExisting) -> {
            if (replaceExisting && armed.compareAndSet(true, false)) {
                write(target, directMarkdown("cas", "SSH 修改"), StandardOpenOption.TRUNCATE_EXISTING);
            }
        });
        Post created = store.createDraft(command("cas", "原始正文"));
        armed.set(true);

        BlogException exception = assertThrows(BlogException.class,
                () -> store.update("cas", created.version(), command("cas", "网页修改")));

        assertEquals(BlogErrorCode.VERSION_CONFLICT, exception.code());
        assertTrue(Files.readString(data.resolve("drafts/cas.md")).contains("SSH 修改"));
    }

    @Test
    void publishRejectsSymlinkedTargetDirectoryWithoutWritingOutside() throws Exception {
        Path data = temporaryDirectory.resolve("data");
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectory(outside);
        FileContentStore store = store(data, (source, target, replaceExisting) -> {
        });
        Post draft = store.createDraft(command("safe-publish", "正文"));
        Files.delete(data.resolve("posts"));
        Files.createSymbolicLink(data.resolve("posts"), outside);

        BlogException exception = assertThrows(BlogException.class,
                () -> store.transition("safe-publish", PostStatus.PUBLISHED, draft.version()));

        assertEquals(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, exception.code());
        assertFalse(Files.exists(outside.resolve("safe-publish.md")));
        assertTrue(Files.exists(data.resolve("drafts/safe-publish.md")));
    }

    @Test
    void archiveRejectsSymlinkedTargetDirectoryWithoutWritingOutside() throws Exception {
        Path data = temporaryDirectory.resolve("data");
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectory(outside);
        FileContentStore store = store(data, (source, target, replaceExisting) -> {
        });
        Post draft = store.createDraft(command("safe-archive", "正文"));
        Files.delete(data.resolve("archive"));
        Files.createSymbolicLink(data.resolve("archive"), outside);

        BlogException exception = assertThrows(BlogException.class,
                () -> store.archive("safe-archive", draft.version()));

        assertEquals(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, exception.code());
        try (var entries = Files.list(outside)) {
            assertEquals(0, entries.count());
        }
        assertTrue(Files.exists(data.resolve("drafts/safe-archive.md")));
    }

    @Test
    void transitionNeverOverwritesTargetCreatedAfterInitialExistenceCheck() throws Exception {
        Path data = temporaryDirectory.resolve("data");
        AtomicBoolean injected = new AtomicBoolean();
        byte[] external = directMarkdown("transition-race", "外部发布内容");
        FileContentStore store = store(data, (source, target, replaceExisting) -> {
            if (!replaceExisting && target.getParent().endsWith("posts")
                    && injected.compareAndSet(false, true)) {
                write(target, external, StandardOpenOption.CREATE_NEW);
            }
        });
        Post draft = store.createDraft(command("transition-race", "草稿正文"));

        BlogException exception = assertThrows(BlogException.class,
                () -> store.transition("transition-race", PostStatus.PUBLISHED, draft.version()));

        assertEquals(BlogErrorCode.SLUG_CONFLICT, exception.code());
        assertEquals(new String(external, StandardCharsets.UTF_8),
                Files.readString(data.resolve("posts/transition-race.md")));
        assertTrue(Files.exists(data.resolve("drafts/transition-race.md")));
    }

    private FileContentStore store(Path data, BeforeFileCommit beforeFileCommit) {
        return new FileContentStore(new FileContentStoreSettings(data, 1_048_576, 8_388_608),
                CLOCK, beforeFileCommit);
    }

    private static PostWriteCommand command(String slug, String body) {
        return new PostWriteCommand(slug, "标题", "", Instant.parse("2026-08-02T08:00:00Z"),
                List.of(), null, body);
    }

    private static byte[] directMarkdown(String slug, String body) {
        return ("---\ntitle: 外部文章\nslug: " + slug
                + "\npublishedAt: 2026-08-02T08:00:00Z\n---\n\n" + body + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void write(Path target, byte[] bytes, StandardOpenOption option) {
        try {
            Files.write(target, bytes, option, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
