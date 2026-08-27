package com.speaive.blog.application.command.account;

public record CreateInvitationCommand(int validDays, int maxUses) {
}
