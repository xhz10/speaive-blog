package com.speaive.blog.application.command.account;

/** 站长创建邀请时的配额、有效期和可选备注。 */
public record CreateInvitationCommand(int validDays, int maxUses, String note) {
    public CreateInvitationCommand(int validDays, int maxUses) { this(validDays, maxUses, null); }
}
