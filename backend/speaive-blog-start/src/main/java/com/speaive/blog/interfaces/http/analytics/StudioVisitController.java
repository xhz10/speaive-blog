package com.speaive.blog.interfaces.http.analytics;

import com.speaive.blog.application.port.in.analytics.VisitAnalyticsUseCase;
import com.speaive.blog.application.query.analytics.VisitAnalyticsQuery;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 管理员专用的统计查询；Security 的 /studio/** 规则同时保护 API 与页面。 */
@RestController
@RequestMapping("/api/v1/studio/analytics")
public class StudioVisitController {
    private final VisitAnalyticsUseCase visits;
    private final VisitHttpMapper mapping;
    public StudioVisitController(VisitAnalyticsUseCase visits, VisitHttpMapper mapping) { this.visits = visits; this.mapping = mapping; }
    @GetMapping
    ResponseEntity<VisitResponses.Overview> overview(@RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) String postId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mapping.toResponse(visits.overview(new VisitAnalyticsQuery(days, page, pageSize, postId))));
    }
}
