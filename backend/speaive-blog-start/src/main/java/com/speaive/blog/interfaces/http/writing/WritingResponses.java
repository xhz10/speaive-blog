package com.speaive.blog.interfaces.http.writing;

import com.speaive.blog.interfaces.http.post.PostResponses;
import java.time.Instant;
import java.util.List;

/** 会员写作 HTTP 响应，不暴露密码哈希、密钥或持久化对象。 */
final class WritingResponses {
    private WritingResponses() { }
    record Preview(String title, String html) { }
    record Account(String id, String username, String displayName, String role, boolean canPublish,
            boolean encryptionAllowed, boolean contentEncrypted, boolean encryptionAvailable, long version) { }
    record Accounts(List<Account> items) { }
    record Posts(String username, String displayName, long total, int page, int pageSize, List<PostResponses.PostSummary> items) { }
    record Community(long total, int page, int pageSize, List<PostResponses.PostSummary> items) { }
    record History(List<Revision> items) { }
    record Revision(long revision, String title, Instant updatedAt, String status) { }
}
