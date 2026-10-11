package com.speaive.blog.infrastructure.content.persistence.mapping;

import com.speaive.blog.domain.account.Invitation;
import com.speaive.blog.domain.account.PasswordReset;
import com.speaive.blog.infrastructure.content.persistence.po.BlogInvitationPo;
import com.speaive.blog.infrastructure.content.persistence.po.PasswordResetPo;
import org.mapstruct.*;

/** 邀请与找回凭证结构映射；原始令牌、领域消费规则均不进入 PO。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AccountRecoveryPersistenceMapStructMapper {
    BlogInvitationPo invitation(Invitation value);
    @Mapping(target = "revoke", ignore = true)
    @Mapping(target = "consume", ignore = true)
    Invitation invitation(BlogInvitationPo value);
    PasswordResetPo reset(PasswordReset value);
    PasswordReset reset(PasswordResetPo value);
}
