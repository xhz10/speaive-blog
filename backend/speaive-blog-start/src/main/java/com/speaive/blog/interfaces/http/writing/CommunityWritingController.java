package com.speaive.blog.interfaces.http.writing;

import com.speaive.blog.application.port.in.post.MemberWritingUseCase;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 朋友公开作品入口；不列会员账号目录，不暴露只有私密内容的作者。 */
@RestController
@RequestMapping("/api/v1/public/community/posts")
public class CommunityWritingController {
    private final MemberWritingUseCase posts;
    private final WritingHttpMapper mapper;
    public CommunityWritingController(MemberWritingUseCase posts, WritingHttpMapper mapper) { this.posts = posts; this.mapper = mapper; }
    @GetMapping
    ResponseEntity<WritingResponses.Community> list(@RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.response(posts.community(page)));
    }
}
