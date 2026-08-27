package com.speaive.blog.interfaces.http.account;

import java.time.Instant;
import java.util.List;

final class InvitationResponses {
    private InvitationResponses() {
    }

    record InvitationDetail(
            String id,
            String code,
            int maxUses,
            int usedCount,
            Instant expiresAt,
            Instant createdAt
    ) {
    }

    record InvitationList(List<InvitationDetail> items) {
    }
}
