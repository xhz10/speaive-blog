package com.speaive.blog.interfaces.http.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;

class VisitRequestGuardTests {
    @Test
    void trustsOnlyConfiguredPeerNetworksAndSingleLiteralAddresses() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10"); request.addHeader("X-Forwarded-For", "8.8.8.8");
        var guard = new VisitRequestGuard(true, "127.0.0.0/8", Clock.systemUTC());
        assertEquals("203.0.113.10", guard.clientIp(request));
        request.setRemoteAddr("127.0.0.1");
        assertEquals("8.8.8.8", guard.clientIp(request));
        assertEquals("127.0.0.1", new VisitRequestGuard(false, "127.0.0.0/8", Clock.systemUTC()).clientIp(request));
        request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For", "8.8.8.8, 1.1.1.1");
        assertEquals("127.0.0.1", guard.clientIp(request));
        request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For", "example.com");
        assertEquals("127.0.0.1", guard.clientIp(request));
    }
    @Test
    void limitsWritesWithoutAnUnboundedAddressMap() {
        var guard = new VisitRequestGuard(false, "127.0.0.0/8", Clock.systemUTC());
        for (int n = 0; n < 120; n++) guard.consume("1.2.3.4");
        assertThrows(com.speaive.blog.interfaces.http.error.ApiHttpException.class, () -> guard.consume("1.2.3.4"));
        assertDoesNotThrow(() -> guard.consume("2.3.4.5"));
    }
}
