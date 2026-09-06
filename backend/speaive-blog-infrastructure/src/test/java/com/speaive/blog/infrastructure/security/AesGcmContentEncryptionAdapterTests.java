package com.speaive.blog.infrastructure.security;

import com.speaive.blog.application.error.BlogException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class AesGcmContentEncryptionAdapterTests {
    @TempDir Path directory;
    private static final String FIRST = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String SECOND = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Test
    void authenticatedEncryptionUsesFreshNoncesAndRejectsSwappedOrDamagedRecords() throws Exception {
        var adapter = adapter("active=first\nkeys.first=" + FIRST);
        String encrypted = adapter.encrypt("owner|post|1", "私密标题与正文");
        assertThat(encrypted).startsWith("v1.first.").doesNotContain("私密");
        assertThat(adapter.decrypt("owner|post|1", encrypted)).isEqualTo("私密标题与正文");
        assertThat(adapter.encrypt("owner|post|1", "私密标题与正文")).isNotEqualTo(encrypted);
        assertThatThrownBy(() -> adapter.decrypt("another-owner|post|1", encrypted)).isInstanceOf(BlogException.class);
        String[] parts = encrypted.split("\\.");
        byte[] bytes = Base64.getUrlDecoder().decode(parts[3]); bytes[0] ^= 1;
        String damaged = String.join(".", parts[0], parts[1], parts[2], Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        assertThatThrownBy(() -> adapter.decrypt("owner|post|1", damaged)).isInstanceOf(BlogException.class);
        for (String malformed : new String[] { "plaintext", "v2.first.abc.xyz", "v1.missing.abc.xyz", "v1.first.!.!", "v1.first.YQ.Yg" }) {
            assertThatThrownBy(() -> adapter.decrypt("owner|post|1", malformed)).isInstanceOf(BlogException.class);
        }
    }

    @Test
    void rotationWritesNewKeyAndRetainsOldCiphertextReadability() throws Exception {
        var first = adapter("active=first\nkeys.first=" + FIRST);
        String old = first.encrypt("context", "旧版本");
        var rotated = adapter("active=second\nkeys.first=" + FIRST + "\nkeys.second=" + SECOND);
        assertThat(rotated.decrypt("context", old)).isEqualTo("旧版本");
        assertThat(rotated.encrypt("context", "新版本")).startsWith("v1.second.");
        var lost = adapter("active=second\nkeys.second=" + SECOND);
        assertThatThrownBy(() -> lost.decrypt("context", old)).isInstanceOf(BlogException.class);
    }

    @Test
    void missingKeysDisableOnlyEncryptionAndMalformedKeysFailStartupWithoutEchoingSecrets() throws Exception {
        var unavailable = new AesGcmContentEncryptionAdapter(directory.resolve("missing"));
        assertThat(unavailable.available()).isFalse();
        assertThatThrownBy(() -> unavailable.encrypt("context", "私密")).isInstanceOf(BlogException.class);
        assertThatThrownBy(() -> adapter("active=first\nkeys.first=secret-invalid-key"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secret-invalid-key");
    }
    private AesGcmContentEncryptionAdapter adapter(String properties) throws Exception {
        Path file = directory.resolve("keys.properties"); Files.writeString(file, properties);
        return new AesGcmContentEncryptionAdapter(file);
    }
}
