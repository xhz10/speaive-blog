package com.speaive.blog.infrastructure.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
@TableName("blog_user")
public class BlogAuthorPo {
    @TableId(type = IdType.INPUT)
    private String id;
    private String username;
    @TableField("display_name")
    private String displayName;
    private AuthorTypePo type;
    @TableField("avatar_url")
    private String avatarUrl;
    private AuthorStatusPo status;

    public BlogAuthorPo() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public AuthorTypePo getType() {
        return type;
    }

    public void setType(AuthorTypePo type) {
        this.type = type;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public AuthorStatusPo getStatus() {
        return status;
    }

    public void setStatus(AuthorStatusPo status) {
        this.status = status;
    }
}
