package com.speaive.blog.interfaces.http.writing;

import com.speaive.blog.application.port.in.account.WritingAccountUseCase;
import com.speaive.blog.application.port.in.post.MemberWritingUseCase;
import com.speaive.blog.interfaces.http.post.PostHttpMapper;
import com.speaive.blog.interfaces.http.post.PostResponses.PostDetail;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 会员自己的写作入口；ROLE_MEMBER 认证与 CSRF 由 Security 校验，业务权限由用例逐次核验。 */
@RestController
@RequestMapping("/api/v1/account/writing")
public class MemberWritingController {
    private final MemberWritingUseCase posts;
    private final WritingAccountUseCase accounts;
    private final WritingHttpMapper mapper;
    private final PostHttpMapper postMapper;
    public MemberWritingController(MemberWritingUseCase posts, WritingAccountUseCase accounts,
            WritingHttpMapper mapper, PostHttpMapper postMapper) {
        this.posts = posts; this.accounts = accounts; this.mapper = mapper; this.postMapper = postMapper;
    }
    @GetMapping("/settings")
    ResponseEntity<WritingResponses.Account> settings(Authentication auth) { return ok(mapper.response(accounts.get(auth.getName()))); }
    @PutMapping("/settings/encryption")
    ResponseEntity<WritingResponses.Account> encryption(Authentication auth, @Valid @RequestBody WritingRequests.Encryption request) {
        return ok(mapper.response(accounts.setEncryption(auth.getName(), mapper.command(request))));
    }
    @GetMapping("/posts")
    ResponseEntity<WritingResponses.Posts> list(Authentication auth, @RequestParam(defaultValue = "1") int page) {
        return ok(mapper.response(posts.listOwn(auth.getName(), page)));
    }
    @GetMapping("/posts/{slug}")
    ResponseEntity<PostDetail> get(Authentication auth, @PathVariable String slug) { return ok(postMapper.toResponse(posts.getOwn(auth.getName(), slug))); }
    @PostMapping("/posts")
    ResponseEntity<PostDetail> create(Authentication auth, @Valid @RequestBody WritingRequests.Write request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(postMapper.toResponse(posts.create(auth.getName(), mapper.command(request))));
    }
    @PutMapping("/posts/{slug}")
    ResponseEntity<PostDetail> update(Authentication auth, @PathVariable String slug, @Valid @RequestBody WritingRequests.Update request) {
        return ok(postMapper.toResponse(posts.update(auth.getName(), slug, request.version(), mapper.command(request))));
    }
    @PostMapping("/posts/{slug}/publish")
    ResponseEntity<PostDetail> publish(Authentication auth, @PathVariable String slug, @Valid @RequestBody WritingRequests.Version request) {
        return ok(postMapper.toResponse(posts.publish(auth.getName(), slug, request.version())));
    }
    @PostMapping("/posts/{slug}/unpublish")
    ResponseEntity<PostDetail> unpublish(Authentication auth, @PathVariable String slug, @Valid @RequestBody WritingRequests.Version request) {
        return ok(postMapper.toResponse(posts.unpublish(auth.getName(), slug, request.version())));
    }
    @PostMapping("/posts/{slug}/archive")
    ResponseEntity<Void> archive(Authentication auth, @PathVariable String slug, @Valid @RequestBody WritingRequests.Version request) {
        posts.archive(auth.getName(), slug, request.version());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/posts/{slug}/history")
    ResponseEntity<WritingResponses.History> history(Authentication auth, @PathVariable String slug) {
        return ok(mapper.response(posts.history(auth.getName(), slug)));
    }
    @PostMapping("/posts/{slug}/restore")
    ResponseEntity<PostDetail> restore(Authentication auth, @PathVariable String slug, @Valid @RequestBody WritingRequests.Restore request) {
        return ok(postMapper.toResponse(posts.restore(auth.getName(), slug, request.revision(), request.version())));
    }
    private static <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
