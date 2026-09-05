package com.speaive.blog.interfaces.http.analytics;

import com.speaive.blog.application.port.in.analytics.VisitAnalyticsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 匿名文章阅读采集；只写入不返回明细，保留 CSRF 校验并排除已登录管理员。 */
@RestController
@RequestMapping("/api/v1/public/posts")
public class PublicVisitController {
    private final VisitAnalyticsUseCase visits;
    private final VisitHttpMapper mapping;
    private final VisitRequestGuard guard;
    public PublicVisitController(VisitAnalyticsUseCase visits, VisitHttpMapper mapping, VisitRequestGuard guard) {
        this.visits = visits; this.mapping = mapping; this.guard = guard;
    }
    @PostMapping("/{slug}/visits")
    ResponseEntity<Void> record(@PathVariable String slug, @Valid @RequestBody VisitRequests.RecordVisit body,
            HttpServletRequest request) {
        if (!request.isUserInRole("ADMIN")) {
            String ip = guard.clientIp(request);
            guard.consume(ip);
            String ua = request.getHeader("User-Agent");
            visits.record(slug, mapping.toCommand(body, ip, ua == null ? "" : ua.substring(0, Math.min(ua.length(), 1024))));
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
