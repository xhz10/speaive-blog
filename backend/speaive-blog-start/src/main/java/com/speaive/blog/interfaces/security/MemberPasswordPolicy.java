package com.speaive.blog.interfaces.security;

import com.speaive.blog.interfaces.http.error.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.charset.StandardCharsets;

/** 注册、改密与找回共享 BCrypt 边界；字节限制先于 encode，避免非 ASCII 密码触发 500。 */
public final class MemberPasswordPolicy {
    private MemberPasswordPolicy() {}
    public static String encode(String password, PasswordEncoder encoder) {
        if (password == null || password.isBlank() || password.length() < 10 || password.length() > 100
                || !fitsBcryptLimit(password)) {
            throw new ApiHttpException(ApiErrorCode.INVALID_REQUEST,
                    "密码长度必须为 10 到 100 个字符，且 UTF-8 编码不能超过 72 字节", HttpStatus.BAD_REQUEST);
        }
        return encoder.encode(password);
    }
    public static boolean matches(String password, String hash, PasswordEncoder encoder) {
        return fitsBcryptLimit(password) && encoder.matches(password, hash);
    }
    /** BCrypt 校验会截断超过 72 字节的输入，登录也必须拒绝该输入，保持与建密规则一致。 */
    static boolean fitsBcryptLimit(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
