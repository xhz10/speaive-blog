package com.speaive.blog.interfaces.http.account;

import com.speaive.blog.application.port.in.account.PasswordManagementUseCase;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 仅站长可签发一次性链接；签发不改密码、不退出会员会话，也不读取会员内容。 */
@RestController
@RequestMapping("/api/v1/studio/members")
public class StudioPasswordRecoveryController {
    private final PasswordManagementUseCase passwords;
    private final PasswordRecoveryHttpMapper mapper;
    public StudioPasswordRecoveryController(PasswordManagementUseCase passwords, PasswordRecoveryHttpMapper mapper) {
        this.passwords = passwords; this.mapper = mapper;
    }
    @PostMapping("/{username}/password-reset")
    ResponseEntity<PasswordRecoveryResponses.Issued> issue(@PathVariable String username) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.response(passwords.issueReset(username)));
    }
}
