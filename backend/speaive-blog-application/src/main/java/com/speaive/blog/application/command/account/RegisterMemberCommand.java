package com.speaive.blog.application.command.account;

public record RegisterMemberCommand(
        String username,
        String displayName,
        String passwordHash,
        String invitationCode
) {
}
