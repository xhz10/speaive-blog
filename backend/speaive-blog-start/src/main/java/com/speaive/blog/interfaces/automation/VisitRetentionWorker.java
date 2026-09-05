package com.speaive.blog.interfaces.automation;

import com.speaive.blog.application.port.in.analytics.VisitAnalyticsUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每小时清理最多一万条过期访问，清理失败会记录日志并在下轮重试。 */
@Component
public final class VisitRetentionWorker {
    private final VisitAnalyticsUseCase visits;
    public VisitRetentionWorker(VisitAnalyticsUseCase visits) { this.visits = visits; }
    @Scheduled(fixedDelayString = "${speaive.analytics.cleanup-interval:1h}", initialDelayString = "1m")
    public void purge() { visits.purgeExpired(); }
}
