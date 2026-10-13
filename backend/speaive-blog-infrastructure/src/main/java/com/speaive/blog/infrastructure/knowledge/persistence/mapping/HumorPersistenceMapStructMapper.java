package com.speaive.blog.infrastructure.knowledge.persistence.mapping;

import com.speaive.blog.domain.knowledge.AgentHumorProfile;
import com.speaive.blog.domain.knowledge.HumorMaterial;
import com.speaive.blog.infrastructure.knowledge.persistence.po.AgentHumorProfilePo;
import com.speaive.blog.infrastructure.knowledge.persistence.po.HumorMaterialPo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface HumorPersistenceMapStructMapper {
    HumorMaterialPo toPo(HumorMaterial material);

    HumorMaterial toDomain(HumorMaterialPo material);

    AgentHumorProfile toDomain(AgentHumorProfilePo profile);
}
