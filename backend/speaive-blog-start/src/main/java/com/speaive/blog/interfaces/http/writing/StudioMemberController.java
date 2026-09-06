package com.speaive.blog.interfaces.http.writing;

import com.speaive.blog.application.port.in.account.WritingAccountUseCase;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 仅管理员可授予写作、公开发布和加密资格；不提供读取会员草稿或替其关闭加密的入口。 */
@RestController
@RequestMapping("/api/v1/studio/members")
public class StudioMemberController {
    private final WritingAccountUseCase accounts;
    private final WritingHttpMapper mapper;
    public StudioMemberController(WritingAccountUseCase accounts, WritingHttpMapper mapper) { this.accounts = accounts; this.mapper = mapper; }
    @GetMapping
    ResponseEntity<WritingResponses.Accounts> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.response(accounts.listMembers()));
    }
    @PutMapping("/{username}/permissions")
    ResponseEntity<WritingResponses.Account> permissions(@PathVariable String username, @Valid @RequestBody WritingRequests.Permissions request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.response(accounts.setPermissions(username, mapper.command(request))));
    }
}
