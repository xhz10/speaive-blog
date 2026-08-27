package com.speaive.blog.interfaces.http.account;

import com.speaive.blog.application.port.in.account.MemberAccountUseCase;
import com.speaive.blog.interfaces.http.account.InvitationRequests.CreateInvitationRequest;
import com.speaive.blog.interfaces.http.account.InvitationResponses.InvitationDetail;
import com.speaive.blog.interfaces.http.account.InvitationResponses.InvitationList;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studio/invitations")
public class StudioInvitationController {
    private final MemberAccountUseCase accounts;
    private final InvitationHttpMapper mapper;

    public StudioInvitationController(MemberAccountUseCase accounts, InvitationHttpMapper mapper) {
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @GetMapping
    InvitationList list() {
        return mapper.toResponse(accounts.listInvitations());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InvitationDetail create(@Valid @RequestBody CreateInvitationRequest request) {
        return mapper.toResponse(accounts.createInvitation(mapper.toCommand(request)));
    }
}
