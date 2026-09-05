package com.speaive.blog.interfaces.http.analytics;

import com.speaive.blog.interfaces.http.error.ApiErrorCode;
import com.speaive.blog.interfaces.http.error.ApiHttpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.net.InetAddress;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 采集入口的可信代理解析与有限内存限流；不影响读取正文的请求。 */
public final class VisitRequestGuard {
    private final boolean trustProxy;
    private final List<IpAddressMatcher> trustedNetworks;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();
    private long lastMinute = -1;

    public VisitRequestGuard(boolean trustProxy, String trustedNetworks, Clock clock) {
        this.trustProxy = trustProxy; this.clock = clock;
        this.trustedNetworks = java.util.Arrays.stream(trustedNetworks.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public String clientIp(HttpServletRequest request) {
        // Spring 的 ForwardedHeaderFilter 可能已包装 remoteAddr，必须先取原始连接校验代理身份。
        HttpServletRequest source = request;
        while (source instanceof HttpServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest original) source = original;
        String remote = normalize(source.getRemoteAddr());
        if (trustProxy && trustedNetworks.stream().anyMatch(network -> network.matches(remote))) {
            String header = source.getHeader("X-Forwarded-For");
            // Astro 代理必须覆盖为单个已校验 IP，不能接受浏览器提交的地址链。
            if (header != null && !header.contains(",")) {
                String candidate = normalize(header);
                if (!candidate.isEmpty()) return candidate;
            }
        }
        return remote;
    }

    public synchronized void consume(String ip) {
        long minute = clock.instant().getEpochSecond() / 60;
        if (minute != lastMinute) { windows.clear(); lastMinute = minute; }
        Window window = windows.get(ip);
        if ((window == null && windows.size() >= 10000) || (window != null && window.count >= 120)) {
            throw new ApiHttpException(ApiErrorCode.RATE_LIMITED, "访问记录提交过于频繁", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (window == null) windows.put(ip, new Window());
        else window.count++;
    }

    private static String normalize(String ip) {
        if (ip == null || ip.isBlank()) return "";
        try { return InetAddress.ofLiteral(ip.trim()).getHostAddress(); }
        catch (IllegalArgumentException e) { return ""; }
    }

    private static final class Window { private int count = 1; }
}
