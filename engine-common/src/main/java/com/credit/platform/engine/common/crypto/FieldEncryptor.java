package com.credit.platform.engine.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 字段级加密工具。
 * <p>
 * 用于机密级变量的加密存储和解密读取。采用 AES-256-GCM 模式，
 * 提供认证加密 (AEAD)，防止密文被篡改。
 * </p>
 *
 * <h3>特性:</h3>
 * <ul>
 *   <li>AES-256-GCM 认证加密 (AEAD)</li>
 *   <li>每次加密使用随机 12 字节 IV，保证相同明文产生不同密文</li>
 *   <li>128-bit 认证标签，防止密文篡改</li>
 *   <li>Base64 编码输出，适合数据库存储</li>
 *   <li>线程安全</li>
 * </ul>
 *
 * <pre>
 * // 初始化 (密钥为 32 字节的 Base64 编码字符串)
 * FieldEncryptor encryptor = new FieldEncryptor("your-base64-encoded-32-byte-key");
 *
 * // 加密
 * String encrypted = encryptor.encrypt("敏感数据");
 *
 * // 解密
 * String decrypted = encryptor.decrypt(encrypted);
 * </pre>
 */
public final class FieldEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;       // GCM 推荐 IV 长度
    private static final int TAG_LENGTH = 128;      // 认证标签位数
    private static final int KEY_LENGTH = 32;       // AES-256 密钥长度 (字节)

    /** 密钥版本前缀，用于支持密钥轮转。新密文格式: v1:Base64(IV||密文||tag)，旧格式: Base64(IV||密文||tag) */
    static final String KEY_VERSION_PREFIX = "v1:";

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom;

    /**
     * 使用 Base64 编码的密钥创建加密器。
     *
     * @param base64Key Base64 编码的 32 字节密钥
     * @throws IllegalArgumentException 如果密钥长度不正确
     */
    public FieldEncryptor(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != KEY_LENGTH) {
            Arrays.fill(keyBytes, (byte) 0);
            throw new IllegalArgumentException(
                "AES-256 key must be 32 bytes, got " + keyBytes.length + " bytes");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        Arrays.fill(keyBytes, (byte) 0);
        this.secureRandom = new SecureRandom();
    }

    /**
     * 使用原始密钥字节数组创建加密器。
     *
     * @param keyBytes 32 字节密钥
     * @throws IllegalArgumentException 如果密钥长度不正确
     */
    public FieldEncryptor(byte[] keyBytes) {
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException(
                "AES-256 key must be 32 bytes, got " + keyBytes.length + " bytes");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    /**
     * 加密明文字符串。
     * <p>
     * 输出格式: v1:Base64(IV || 密文 || 认证标签)
     * </p>
     *
     * @param plaintext 明文
     * @return 版本前缀 + Base64 编码的密文 (包含 IV)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (plaintext.isEmpty()) return "";

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + 密文(含认证标签)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return KEY_VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new EncryptionException("AES encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解密密文字符串。
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>v1:Base64(IV || 密文 || 认证标签) — 带版本前缀的新格式</li>
     *   <li>Base64(IV || 密文 || 认证标签) — 无前缀的旧格式 (向后兼容)</li>
     * </ul>
     *
     * @param ciphertext 版本前缀 + Base64 编码的密文，或纯 Base64 密文 (旧格式)
     * @return 明文
     * @throws DecryptionException 如果解密失败 (密钥错误或数据被篡改)
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        if (ciphertext.isEmpty()) return "";

        // 剥离版本前缀 (向后兼容无前缀的旧密文)
        String base64Data = ciphertext;
        if (ciphertext.startsWith(KEY_VERSION_PREFIX)) {
            base64Data = ciphertext.substring(KEY_VERSION_PREFIX.length());
        }

        try {
            byte[] combined = Base64.getDecoder().decode(base64Data);

            if (combined.length < IV_LENGTH + 16) {
                throw new DecryptionException("Ciphertext too short");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (DecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new DecryptionException("AES decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * 生成随机 32 字节 AES-256 密钥 (Base64 编码)。
     *
     * @return Base64 编码的密钥
     */
    public static String generateKey() {
        byte[] key = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 检查字符串是否为加密格式。
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>v1:Base64(...) — 带版本前缀的新格式</li>
     *   <li>Base64(...) — 无前缀的旧格式</li>
     * </ul>
     * 验证规则：Base64 部分解码后长度至少为 IV(12) + GCM认证标签(16) + 最短密文(1) = 29 字节，
     * 且前 IV_LENGTH 字节作为 IV 不应全部为零（排除普通长字符串的误判）。
     * </p>
     *
     * @param value 待检查字符串
     * @return true 如果可能是加密数据
     */
    public static boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) return false;

        // 剥离版本前缀
        String base64Part = value;
        if (value.startsWith(KEY_VERSION_PREFIX)) {
            base64Part = value.substring(KEY_VERSION_PREFIX.length());
        }

        // 加密输出为 Base64，必须只包含 Base64 字符 (允许末尾有 = 填充)
        if (!base64Part.matches("^[A-Za-z0-9+/]+=*$")) return false;
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Part);
            // 最短有效密文 = IV(12) + GCM tag(16) + 至少1字节密文 = 29
            if (decoded.length <= IV_LENGTH + TAG_LENGTH / 8) return false;
            // 检查前 IV_LENGTH 字节不全为零（真实 IV 由 SecureRandom 生成，几乎不可能全零）
            boolean ivAllZero = true;
            for (int i = 0; i < IV_LENGTH; i++) {
                if (decoded[i] != 0) {
                    ivAllZero = false;
                    break;
                }
            }
            return !ivAllZero;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ========== 异常类 ==========

    /**
     * 加密异常。
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 解密异常。
     */
    public static class DecryptionException extends RuntimeException {
        public DecryptionException(String message) {
            super(message);
        }
        public DecryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
