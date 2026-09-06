package com.speaive.blog.infrastructure.security;

import com.speaive.blog.application.error.BlogErrorCode;
import com.speaive.blog.application.error.BlogException;
import com.speaive.blog.application.port.out.security.ContentEncryptionPort;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 使用 JDK 标准 AES-256-GCM 实现数据库内容的认证加密。
 * 密钥环单独存放于受权限保护的文件；密文携带密钥编号，轮换时必须保留旧密钥。
 * 没有配置密钥时仅关闭加密功能；已有密文永远不能降级为明文解析。
 */
public final class AesGcmContentEncryptionAdapter implements ContentEncryptionPort {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final Map<String, SecretKeySpec> keys;
    private final String activeKey;
    private final SecureRandom random = new SecureRandom();

    public AesGcmContentEncryptionAdapter(Path keyFile) {
        if (!Files.exists(keyFile)) {
            keys = Map.of(); activeKey = null; return;
        }
        try (var input = Files.newInputStream(keyFile)) {
            Properties properties = new Properties();
            properties.load(input);
            activeKey = properties.getProperty("active", "").trim();
            Map<String, SecretKeySpec> loaded = new HashMap<>();
            for (String name : properties.stringPropertyNames()) {
                if (!name.startsWith("keys.")) continue;
                String id = name.substring(5);
                if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
                byte[] bytes = Base64.getDecoder().decode(properties.getProperty(name).trim());
                if (bytes.length != 32) throw new IllegalArgumentException();
                loaded.put(id, new SecretKeySpec(bytes, "AES"));
                java.util.Arrays.fill(bytes, (byte) 0);
            }
            if (!loaded.containsKey(activeKey)) throw new IllegalArgumentException();
            keys = Map.copyOf(loaded);
        } catch (Exception exception) {
            // 不包含底层异常文本，避免错误配置中的密钥字符串进入日志。
            throw new IllegalStateException("内容加密密钥文件无效，请检查 active、密钥编号和 32 字节 Base64 密钥");
        }
    }

    @Override
    public boolean available() { return activeKey != null; }

    @Override
    public String encrypt(String context, String plaintext) {
        if (!available()) throw failure();
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            String header = "v1." + activeKey;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKey), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(header, context));
            return header + "." + ENCODER.encodeToString(nonce) + "."
                    + ENCODER.encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw failure();
        }
    }

    @Override
    public String decrypt(String context, String ciphertext) {
        try {
            String[] parts = ciphertext.split("\\.", -1);
            if (parts.length != 4 || !parts[0].equals("v1") || !keys.containsKey(parts[1])) throw failure();
            byte[] nonce = DECODER.decode(parts[2]);
            byte[] encrypted = DECODER.decode(parts[3]);
            if (nonce.length != 12 || encrypted.length < 16) throw failure();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.get(parts[1]), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(parts[0] + "." + parts[1], context));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw failure();
        }
    }

    private static byte[] aad(String header, String context) {
        return ("speaive-member-content\n" + header + "\n" + context).getBytes(StandardCharsets.UTF_8);
    }

    private static BlogException failure() {
        return new BlogException(BlogErrorCode.STORAGE_ERROR, "加密内容暂时无法读取或保存，请联系站长检查密钥与数据完整性");
    }
}
