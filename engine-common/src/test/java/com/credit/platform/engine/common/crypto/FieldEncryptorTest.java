package com.credit.platform.engine.common.crypto;

import org.junit.jupiter.api.*;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES-256 字段加密器测试。
 */
class FieldEncryptorTest {

    private static final String TEST_KEY = FieldEncryptor.generateKey();
    private FieldEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new FieldEncryptor(TEST_KEY);
    }

    // ========== 基本加密解密 ==========

    @Test
    @DisplayName("加密-解密: 基本字符串")
    void encryptDecrypt_basic() {
        String plaintext = "这是一条敏感信息";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("加密-解密: 英文字符串")
    void encryptDecrypt_english() {
        String plaintext = "sensitive data 12345!@#$%";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("加密-解密: 身份证号")
    void encryptDecrypt_idCard() {
        String idCard = "110101199001011234";
        String encrypted = encryptor.encrypt(idCard);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(idCard, decrypted);
    }

    @Test
    @DisplayName("加密-解密: 手机号")
    void encryptDecrypt_phone() {
        String phone = "13812345678";
        String encrypted = encryptor.encrypt(phone);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(phone, decrypted);
    }

    @Test
    @DisplayName("加密-解密: 长文本")
    void encryptDecrypt_longText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append("测试数据加密");
        String plaintext = sb.toString();
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    // ========== 边界情况 ==========

    @Test
    @DisplayName("加密-解密: null 原样返回")
    void encryptDecrypt_null() {
        assertNull(encryptor.encrypt(null));
        assertNull(encryptor.decrypt(null));
    }

    @Test
    @DisplayName("加密-解密: 空字符串原样返回")
    void encryptDecrypt_empty() {
        assertEquals("", encryptor.encrypt(""));
        assertEquals("", encryptor.decrypt(""));
    }

    @Test
    @DisplayName("加密-解密: 单字符")
    void encryptDecrypt_singleChar() {
        String plaintext = "A";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    // ========== 安全性验证 ==========

    @Test
    @DisplayName("安全: 相同明文产生不同密文 (随机IV)")
    void security_differentCiphertextPerEncryption() {
        String plaintext = "same data";
        String encrypted1 = encryptor.encrypt(plaintext);
        String encrypted2 = encryptor.encrypt(plaintext);
        assertNotEquals(encrypted1, encrypted2, "Same plaintext should produce different ciphertexts");
        // 但都能解密回原文
        assertEquals(plaintext, encryptor.decrypt(encrypted1));
        assertEquals(plaintext, encryptor.decrypt(encrypted2));
    }

    @Test
    @DisplayName("安全: 密文被篡改时解密失败")
    void security_tamperDetection() {
        String encrypted = encryptor.encrypt("test data");
        // 篡改密文
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 2] ^= 0xFF; // 修改最后一个字节
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThrows(FieldEncryptor.DecryptionException.class, () -> encryptor.decrypt(tampered));
    }

    @Test
    @DisplayName("安全: 错误密钥解密失败")
    void security_wrongKey() {
        String encrypted = encryptor.encrypt("secret");
        FieldEncryptor wrongEncryptor = new FieldEncryptor(FieldEncryptor.generateKey());
        assertThrows(FieldEncryptor.DecryptionException.class, () -> wrongEncryptor.decrypt(encrypted));
    }

    @Test
    @DisplayName("安全: 无效Base64解密失败")
    void security_invalidBase64() {
        assertThrows(FieldEncryptor.DecryptionException.class, () -> encryptor.decrypt("not-valid-base64!!!"));
    }

    @Test
    @DisplayName("安全: 过短密文解密失败")
    void security_shortCiphertext() {
        String shortCipher = Base64.getEncoder().encodeToString(new byte[10]);
        assertThrows(FieldEncryptor.DecryptionException.class, () -> encryptor.decrypt(shortCipher));
    }

    // ========== 密钥管理 ==========

    @Test
    @DisplayName("密钥: generateKey 生成有效32字节密钥")
    void keyGeneration_validLength() {
        String key = FieldEncryptor.generateKey();
        byte[] keyBytes = Base64.getDecoder().decode(key);
        assertEquals(32, keyBytes.length);
    }

    @Test
    @DisplayName("密钥: 每次生成不同密钥")
    void keyGeneration_unique() {
        String key1 = FieldEncryptor.generateKey();
        String key2 = FieldEncryptor.generateKey();
        assertNotEquals(key1, key2);
    }

    @Test
    @DisplayName("密钥: 错误长度密钥抛出异常")
    void keyGeneration_wrongLength() {
        assertThrows(IllegalArgumentException.class, () ->
            new FieldEncryptor(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalArgumentException.class, () ->
            new FieldEncryptor(new byte[24]));
    }

    // ========== isEncrypted 检查 ==========

    @Test
    @DisplayName("isEncrypted: 加密数据返回 true")
    void isEncrypted_encryptedData() {
        String encrypted = encryptor.encrypt("test");
        assertTrue(FieldEncryptor.isEncrypted(encrypted));
    }

    @Test
    @DisplayName("isEncrypted: 普通文本返回 false")
    void isEncrypted_plainText() {
        assertFalse(FieldEncryptor.isEncrypted("这是普通文本"));
        assertFalse(FieldEncryptor.isEncrypted("13812345678"));
    }

    @Test
    @DisplayName("isEncrypted: null/空返回 false")
    void isEncrypted_nullEmpty() {
        assertFalse(FieldEncryptor.isEncrypted(null));
        assertFalse(FieldEncryptor.isEncrypted(""));
    }

    // ========== 并发安全性 ==========

    @Test
    @DisplayName("并发: 多线程加密解密一致性")
    void concurrent_encryptDecrypt() throws Exception {
        int threadCount = 10;
        int iterations = 100;
        Thread[] threads = new Thread[threadCount];
        int[] errors = {0};

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < iterations; i++) {
                    String plaintext = "thread_" + threadId + "_data_" + i;
                    String encrypted = encryptor.encrypt(plaintext);
                    String decrypted = encryptor.decrypt(encrypted);
                    if (!plaintext.equals(decrypted)) {
                        errors[0]++;
                    }
                }
            });
            threads[t].start();
        }

        for (Thread t : threads) t.join();
        assertEquals(0, errors[0], "All concurrent encrypt/decrypt should succeed");
    }
}
