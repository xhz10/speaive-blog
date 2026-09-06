package com.speaive.blog.application.port.out.security;

/** 内容加密出站端口。密钥只能存放在数据库之外，应用不可把密钥传入 HTTP 响应。 */
public interface ContentEncryptionPort {
    boolean available();
    /** context 绑定所有者、文章 ID、版本和记录类型，防止交换密文后被当成另一条内容读取。 */
    String encrypt(String context, String plaintext);
    String decrypt(String context, String ciphertext);
}
