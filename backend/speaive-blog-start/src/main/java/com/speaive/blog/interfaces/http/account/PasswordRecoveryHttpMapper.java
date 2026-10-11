package com.speaive.blog.interfaces.http.account;

import com.speaive.blog.application.result.account.PasswordResetResult;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
interface PasswordRecoveryHttpMapper {
    PasswordRecoveryResponses.Issued response(PasswordResetResult result);
}
