package com.speaive.blog.infrastructure.content.persistence.mapping;

import com.speaive.blog.application.result.analytics.VisitAnalyticsResult;
import com.speaive.blog.domain.analytics.ArticleVisit;
import com.speaive.blog.infrastructure.content.persistence.po.ArticleVisitPo;
import com.speaive.blog.infrastructure.content.persistence.po.VisitCountPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/** 访问事件与统计读投影的结构映射；不负责权限、过滤或设备识别。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ArticleVisitPersistenceMapper {
    @Mapping(target = "deviceType", source = "device.type")
    @Mapping(target = "deviceModel", source = "device.model")
    @Mapping(target = "operatingSystem", source = "device.operatingSystem")
    @Mapping(target = "browser", source = "device.browser")
    ArticleVisitPo toPo(ArticleVisit visit);
    VisitAnalyticsResult.Visit toResult(ArticleVisitPo row);
    VisitAnalyticsResult.Count toResult(VisitCountPo row);
}
