package com.speaive.blog.interfaces.http.knowledge;

import com.speaive.blog.application.command.knowledge.SaveHumorMaterialCommand;
import com.speaive.blog.application.result.knowledge.HumorMaterialResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import java.util.List;

@Mapper(componentModel=MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy=ReportingPolicy.ERROR)
interface HumorKnowledgeHttpMapper {
    SaveHumorMaterialCommand toCommand(HumorKnowledgeRequests.SaveMaterialRequest request);
    HumorKnowledgeResponses.MaterialDetail toResponse(HumorMaterialResult result);
    List<HumorKnowledgeResponses.MaterialDetail> toResponses(List<HumorMaterialResult> results);
}
