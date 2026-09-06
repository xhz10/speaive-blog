package com.speaive.blog.interfaces.http.writing;

import com.speaive.blog.application.port.in.post.MemberWritingUseCase;
import com.speaive.blog.interfaces.http.post.PostHttpMapper;
import com.speaive.blog.interfaces.http.post.PostResponses.PostDetail;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 个人主页只读取处于已发布且公开可见状态的文章，私密记录与历史版本不进入公开投影。 */
@RestController
@RequestMapping("/api/v1/public/profiles/{username}")
public class PublicProfileController {
    private final MemberWritingUseCase posts;
    private final WritingHttpMapper mapper;
    private final PostHttpMapper postMapper;
    public PublicProfileController(MemberWritingUseCase posts, WritingHttpMapper mapper, PostHttpMapper postMapper) {
        this.posts = posts; this.mapper = mapper; this.postMapper = postMapper;
    }
    @GetMapping
    ResponseEntity<WritingResponses.Posts> profile(@PathVariable String username, @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.response(posts.profile(username, page)));
    }
    @GetMapping("/posts/{slug}")
    ResponseEntity<PostDetail> post(@PathVariable String username, @PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(postMapper.toResponse(posts.published(username, slug)));
    }
}
