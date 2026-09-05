package com.speaive.blog.interfaces.automation;

import com.speaive.blog.application.port.in.automation.CommunityAutomationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = {"speaive.ai.enabled", "speaive.ai.community-automation-enabled"},
        havingValue = "true")
public final class CommunityCommentAutomationWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommunityCommentAutomationWorker.class);

    private final CommunityAutomationUseCase automation;
    private final int batchSize;

    public CommunityCommentAutomationWorker(
            CommunityAutomationUseCase automation,
            @Value(
                    "${speaive.ai.community-batch-size:3}") int batchSize) {
        this.automation = automation;
        this.batchSize = Math.clamp(batchSize, 1, 20);
    }

    @Scheduled(fixedDelayString = "${speaive.ai.community-scan-interval:15s}")
    public void processDueComments() {
        try {
            var result = automation.processDueJobs(batchSize);
            if (result.processed() > 0) {
                LOGGER.info("社区 Agent 自动评论处理完成: processed={}, succeeded={}, skipped={}, retried={}, failed={}",
                        result.processed(), result.succeeded(), result.skipped(), result.retried(), result.failed());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("社区 Agent 自动评论批次处理失败", exception);
        }
    }
}
