package com.speaive.blog.interfaces.http.media;

import com.speaive.blog.application.result.media.StoredMediaResult;
import com.speaive.blog.interfaces.http.media.MediaResponses.StoredMediaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface MediaHttpMapper {
    StoredMediaResponse toResponse(StoredMediaResult result);
}
