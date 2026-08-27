package com.speaive.blog.application.result.account;

import java.util.List;

public record InvitationListResult(List<InvitationResult> items) {
    public InvitationListResult {
        items = List.copyOf(items);
    }
}
