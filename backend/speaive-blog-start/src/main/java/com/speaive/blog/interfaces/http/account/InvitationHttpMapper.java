package com.speaive.blog.interfaces.http.account;

import com.speaive.blog.application.command.account.CreateInvitationCommand;
import com.speaive.blog.application.result.account.InvitationListResult;
import com.speaive.blog.application.result.account.InvitationResult;
import com.speaive.blog.interfaces.http.account.InvitationResponses.InvitationDetail;
import com.speaive.blog.interfaces.http.account.InvitationResponses.InvitationList;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
interface InvitationHttpMapper {
    CreateInvitationCommand toCommand(InvitationRequests.CreateInvitationRequest request);

    InvitationDetail toResponse(InvitationResult result);

    InvitationList toResponse(InvitationListResult result);
}
