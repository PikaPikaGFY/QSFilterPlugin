package cn.hdbstudio.qsfilter.crypto;

import cn.hdbstudio.qsfilter.config.PluginConfig;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * AES-256-GCM 加密工具。
 * 实现 API 信封层的加密/解密。
 *
 * 信封格式（加密后）:
 *   - iv: Base64 编码的 12 字节 GCM IV
 *   - data: Base64 编码的密文
 *
 * 每个请求使用独立随机 IV，确保语义安全。
 */
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private boolean enabled;

    public EncryptionUtil(PluginConfig config) {
        this.enabled = config.isEncryptionEnabled();
        initKey(config);
    }

    public void reload(PluginConfig config) {
        this.enabled = config.isEncryptionEnabled();
        initKey(config);
    }

    private void initKey(PluginConfig config) {
        if (!enabled) return;

        byte[] keyBytes = config.getSecretKey();
        if (keyBytes == null || keyBytes.length != 32) {
            // 自动生成 256-bit 密钥
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256);
                SecretKey key = keyGen.generateKey();
                config.setSecretKey(key.getEncoded());
                // 持久化密钥到 config.yml
                // 注意：这里需要 plugin data folder，简化处理
                this.secretKey = key;
            } catch (Exception e) {
                throw new RuntimeException("AES 密钥生成失败", e);
            }
        } else {
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        }
    }

    /**
     * 加密明文字符串，返回包含 IV 和密文的结果。
     */
    public EncryptResult encrypt(String plaintext) {
        if (!enabled || secretKey == null) {
            return new EncryptResult(null, plaintext);
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return new EncryptResult(
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext)
            );
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 加密 JSON 字节数组。
     */
    public EncryptResult encrypt(byte[] plaintext) {
        if (!enabled || secretKey == null) {
            return new EncryptResult(null, new String(plaintext, java.nio.charset.StandardCharsets.UTF_8));
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            return new EncryptResult(
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertext)
            );
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    public boolean isEnabled() {
        return enabled && secretKey != null;
    }

    /**
     * 加密结果：IV + 密文。
     */
    public record EncryptResult(String iv, String data) {}
}
