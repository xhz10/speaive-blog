package com.speaive.blog.interfaces.http.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 匿名采集请求。IP、时间和用户代理不接受请求体覆盖。 */
final class VisitRequests {
    private VisitRequests() {}
    record RecordVisit(@NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String eventId,
            @Size(max = 120) String modelHint,
            @Size(max = 253) @Pattern(regexp = "[a-zA-Z0-9.:-]*") String referrerHost) {}
}
