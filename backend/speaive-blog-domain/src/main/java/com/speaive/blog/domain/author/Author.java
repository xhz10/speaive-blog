package com.speaive.blog.domain.author;

import com.speaive.blog.domain.error.DomainErrorCode;
import com.speaive.blog.domain.error.DomainException;

public record Author(
        String id,
        String username,
        String displayName,
        AuthorType type,
        String avatarUrl,
        AuthorStatus status
) {
    public static final String ADMIN_ID = "00000000-0000-0000-0000-000000000001";
    public static final Author ADMIN = new Author(
            ADMIN_ID, "admin", "Speaive", AuthorType.HUMAN, null, AuthorStatus.ACTIVE);

    public Author {
        id = requireText(id, "作者 ID 不能为空", 36);
        username = requireText(username, "作者用户名不能为空", 50).toLowerCase(java.util.Locale.ROOT);
        displayName = requireText(displayName, "作者显示名称不能为空", 100);
        if (type == null || status == null) {
            throw new DomainException(DomainErrorCode.INVALID_AUTHOR, "作者类型和状态不能为空");
        }
        avatarUrl = normalizeAvatarUrl(avatarUrl);
    }

    public boolean canAuthor() {
        return status == AuthorStatus.ACTIVE;
    }

    public Author ensureCanAuthor() {
        if (!canAuthor()) {
            throw new DomainException(DomainErrorCode.INVALID_AUTHOR, "作者已被禁用，不能创建内容");
        }
        return this;
    }

    private static String normalizeAvatarUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, "作者头像地址不能为空", 2048);
    }

    private static String requireText(String value, String message, int maxLength) {
        String result = value == null ? "" : value.trim();
        int length = result.codePointCount(0, result.length());
        if (length == 0 || length > maxLength) {
            throw new DomainException(DomainErrorCode.INVALID_AUTHOR,
                    length == 0 ? message : message.replace("不能为空", "过长"));
        }
        return result;
    }
}
