package com.speaive.blog.interfaces.http.account;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

final class InvitationRequests {
    private InvitationRequests() {
    }

    record CreateInvitationRequest(
            @Min(value = 1, message = "邀请码有效期至少为 1 天")
            @Max(value = 365, message = "邀请码有效期不能超过 365 天")
            int validDays,
            @Min(value = 1, message = "邀请码至少允许使用 1 次")
            @Max(value = 100, message = "邀请码最多允许使用 100 次")
            int maxUses
    ) {
    }
}
