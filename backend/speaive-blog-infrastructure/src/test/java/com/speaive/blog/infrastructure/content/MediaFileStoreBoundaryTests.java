package com.speaive.blog.infrastructure.content;

import com.speaive.blog.application.BlogErrorCode;
import com.speaive.blog.application.BlogException;
import com.speaive.blog.domain.MediaContent;
import com.speaive.blog.domain.StoredMedia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaFileStoreBoundaryTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-02T08:00:00Z"), ZoneOffset.UTC);
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path temporaryDirectory;

    @Test
    void mediaStoreRoundTripDoesNotCreateLegacyContentDirectories() {
        Path data = temporaryDirectory.resolve("media-data");
        MediaFileStore store = new MediaFileStore(settings(data), CLOCK);

        StoredMedia stored = store.store("pixel.png", "image/png", PNG);
        MediaContent content = store.read(stored.relativePath());

        assertArrayEquals(PNG, content.bytes());
        assertTrue(Files.isRegularFile(data.resolve("media").resolve(stored.relativePath())));
        assertFalse(Files.exists(data.resolve("posts")));
        assertFalse(Files.exists(data.resolve("drafts")));
        assertFalse(Files.exists(data.resolve("archive")));
    }

    @Test
    void mediaStoreRejectsASymbolicLinkAsItsMediaDirectory() throws Exception {
        Path data = temporaryDirectory.resolve("linked-data");
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(data);
        Files.createDirectories(outside);
        Files.createSymbolicLink(data.resolve("media"), outside);

        BlogException exception = assertThrows(BlogException.class,
                () -> new MediaFileStore(settings(data), CLOCK));

        assertEquals(BlogErrorCode.PATH_OUTSIDE_DATA_DIR, exception.code());
        assertFalse(Files.exists(outside.resolve("2026")));
    }

    private static ContentStorageSettings settings(Path data) {
        return new ContentStorageSettings(data, 1_048_576, 8_388_608);
    }
}
