package com.speaive.blog.infrastructure.analytics;

import com.speaive.blog.application.port.out.analytics.VisitorContextPort;
import com.speaive.blog.domain.analytics.DeviceType;
import com.speaive.blog.domain.analytics.VisitorDevice;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 离线设备与 IP 归属地解析；不发送网络请求，数据库缺失时降级为“未知”。 */
public final class LocalVisitorContextAdapter implements VisitorContextPort, AutoCloseable {
    private static final Pattern BOT = Pattern.compile("bot|spider|crawler|headless|slurp|curl/|wget/", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANDROID_MODEL = Pattern.compile("Android[^;)]*;\\s*(?:[a-z]{2}(?:[-_][A-Z]{2})?;\\s*)?([^;)]+?)(?: Build/[^;)]*)?[;)]");
    private final Ip2Region regions;

    public LocalVisitorContextAdapter(Path directory) throws Exception {
        Path v4 = directory.resolve("ip2region_v4.xdb");
        Path v6 = directory.resolve("ip2region_v6.xdb");
        Config c4 = Files.isRegularFile(v4) ? Config.custom().setCachePolicy(Config.BufferCache).setXdbPath(v4.toString()).asV4() : null;
        Config c6 = Files.isRegularFile(v6) ? Config.custom().setCachePolicy(Config.BufferCache).setXdbPath(v6.toString()).asV6() : null;
        regions = c4 == null && c6 == null ? null : Ip2Region.create(c4, c6);
    }

    @Override
    public VisitorDevice device(String userAgent, String modelHint) {
        String ua = clean(userAgent, 1024);
        String lower = ua.toLowerCase(Locale.ROOT);
        DeviceType type = BOT.matcher(ua).find() ? DeviceType.BOT
                : lower.contains("ipad") || lower.contains("tablet") || (lower.contains("android") && !lower.contains("mobile")) ? DeviceType.TABLET
                : lower.contains("mobile") || lower.contains("iphone") || lower.contains("ipod") ? DeviceType.MOBILE
                : lower.contains("windows") || lower.contains("macintosh") || lower.contains("x11") || lower.contains("linux") ? DeviceType.DESKTOP
                : DeviceType.UNKNOWN;
        String os = lower.contains("android") ? "Android" : lower.contains("iphone") || lower.contains("ipad") ? "iOS / iPadOS"
                : lower.contains("windows") ? "Windows" : lower.contains("cros") ? "ChromeOS"
                : lower.contains("mac") ? "macOS" : lower.contains("linux") ? "Linux" : "";
        String browser = lower.contains("micromessenger") ? "微信内置浏览器"
                : lower.contains("edg/") || lower.contains("edga/") || lower.contains("edgios/") ? "Microsoft Edge"
                : lower.contains("opr/") ? "Opera" : lower.contains("samsungbrowser/") ? "Samsung Internet"
                : lower.contains("firefox/") || lower.contains("fxios/") ? "Firefox"
                : lower.contains("chrome/") || lower.contains("crios/") ? "Chrome"
                : lower.contains("safari/") ? "Safari" : "";
        String model = clean(modelHint, 120);
        if (model.isBlank() && lower.contains("android")) {
            var match = ANDROID_MODEL.matcher(ua);
            if (match.find()) model = clean(match.group(1), 120);
            // 新版 Chrome 简化 UA 中的 K 是占位值，不能当成设备型号。
            if (model.equals("K")) model = "";
        }
        return new VisitorDevice(type, model, os, browser);
    }

    @Override
    public String location(String ip) {
        try {
            var address = InetAddress.ofLiteral(ip);
            byte[] bytes = address.getAddress();
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc)) return "本地 / 内网";
            if (regions == null) return "未知";
            String found = regions.search(ip);
            if (found == null || found.isBlank()) return "未知";
            // xdb 字段保留顺序，移除占位 0 与相邻重复地区；不推断街道或精确位置。
            String label = Arrays.stream(found.split("\\|")).filter(part -> !part.isBlank() && !part.equals("0"))
                    .distinct().collect(Collectors.joining(" · "));
            return label.isBlank() ? "未知" : clean(label, 200);
        } catch (Exception e) { return "未知"; }
    }

    @Override
    public String visitorKey(String ip, String userAgent) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((ip + "\n" + clean(userAgent, 1024)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String result = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return result.substring(0, Math.min(max, result.length()));
    }

    @Override
    public void close() throws Exception { if (regions != null) regions.close(); }
}
