package com.speaive.blog.interfaces.http.writing;

import com.speaive.blog.application.command.account.*;
import com.speaive.blog.application.command.post.PostWriteCommand;
import com.speaive.blog.application.result.account.*;
import com.speaive.blog.application.result.post.*;
import com.speaive.blog.interfaces.http.post.PostHttpMapper;
import org.mapstruct.*;

/** 会员 HTTP 与用例契约间的编译期结构映射。 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = PostHttpMapper.class)
public interface WritingHttpMapper {
    MemberPermissionsCommand command(WritingRequests.Permissions request);
    ContentEncryptionCommand command(WritingRequests.Encryption request);
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "cover", source = "cover", qualifiedByName = "emptyToNull")
    PostWriteCommand command(WritingRequests.Write request);
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "cover", source = "cover", qualifiedByName = "emptyToNull")
    PostWriteCommand command(WritingRequests.Update request);
    WritingResponses.Account response(WritingAccountResult result);
    WritingResponses.Accounts response(WritingAccountListResult result);
    WritingResponses.Posts response(MemberPostListResult result);
    WritingResponses.History response(MemberPostHistoryResult result);
}
