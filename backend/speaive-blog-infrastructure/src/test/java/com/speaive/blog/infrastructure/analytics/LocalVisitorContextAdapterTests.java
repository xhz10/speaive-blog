package com.speaive.blog.infrastructure.analytics;

import com.speaive.blog.domain.analytics.DeviceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalVisitorContextAdapterTests {
    @TempDir Path directory;
    @Test
    void parsesDeviceHintsWithoutInventingModels() throws Exception {
        try (var adapter = new LocalVisitorContextAdapter(directory)) {
            var android = adapter.device("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/135.0.0.0 Mobile Safari/537.36", "Pixel 9");
            assertEquals(DeviceType.MOBILE, android.type());
            assertEquals("Pixel 9", android.model());
            assertEquals("Chrome", android.browser());
            assertEquals("", adapter.device("Mozilla/5.0 (Linux; Android 10; K) Chrome/135.0.0.0 Mobile Safari/537.36", "").model());
            assertEquals("SM-S928B", adapter.device("Mozilla/5.0 (Linux; Android 14; SM-S928B Build/UP1A) Chrome/130.0 Mobile Safari/537.36", "").model());
            var iphone = adapter.device("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Version/18.0 Mobile Safari/604.1", "");
            assertEquals(DeviceType.MOBILE, iphone.type());
            assertEquals("", iphone.model());
            assertEquals(DeviceType.BOT, adapter.device("Googlebot/2.1", "").type());
            assertEquals(DeviceType.UNKNOWN, adapter.device(null, null).type());
        }
    }
    @Test
    void absentGeoDatabaseAndPrivateIpsAreExplicitAndKeysAreStable() throws Exception {
        try (var adapter = new LocalVisitorContextAdapter(directory)) {
            assertEquals("未知", adapter.location("8.8.8.8"));
            assertEquals("未知", adapter.location("not-an-ip"));
            assertEquals("本地 / 内网", adapter.location("127.0.0.1"));
            assertEquals("本地 / 内网", adapter.location("fd00::1"));
            assertEquals(adapter.visitorKey("1.2.3.4", "A"), adapter.visitorKey("1.2.3.4", "A"));
            assertNotEquals(adapter.visitorKey("1.2.3.4", "A"), adapter.visitorKey("1.2.3.4", "B"));
        }
    }
}
