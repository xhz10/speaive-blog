package com.speaive.blog.interfaces.http.analytics;

import com.speaive.blog.application.command.analytics.RecordArticleVisitCommand;
import com.speaive.blog.application.result.analytics.VisitAnalyticsResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** 访问采集及统计 HTTP 边界的编译期结构映射。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface VisitHttpMapper {
    @Mapping(target = "referrerHost", source = "request.referrerHost", defaultValue = "")
    RecordArticleVisitCommand toCommand(VisitRequests.RecordVisit request, String ip, String userAgent);
    @org.mapstruct.BeanMapping(nullValueMappingStrategy = org.mapstruct.NullValueMappingStrategy.RETURN_DEFAULT)
    com.speaive.blog.application.query.analytics.VisitAnalyticsQuery toQuery(int days, int page, int pageSize, String postId);
    VisitResponses.Overview toResponse(VisitAnalyticsResult result);
    VisitResponses.Visit toResponse(VisitAnalyticsResult.Visit result);
    VisitResponses.Count toResponse(VisitAnalyticsResult.Count result);
}
