package com.credit.platform.admin.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/**
 * JWT 服务 — 纯 Java 实现，不依赖外部 JWT 库。
 * <p>
 * 使用 HMAC-SHA256 签名。Token 格式: base64(header).base64(payload).base64(signature)
 * <ul>
 *   <li>Access Token: 有效期 2 小时</li>
 *   <li>Refresh Token: 有效期 7 天</li>
 * </ul>
 * </p>
 */
@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long ACCESS_TOKEN_VALIDITY_MS = 2 * 60 * 60 * 1000L;   // 2 hours
    private static final long REFRESH_TOKEN_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    /** HMAC 签名密钥 — 生产环境应从配置读取 */
    private final byte[] secretKey;
    /** Refresh Token 存储: token → username */
    private final ConcurrentHashMap<String, RefreshTokenInfo> refreshTokenStore = new ConcurrentHashMap<>();

    public JwtService() {
        // 生成随机密钥
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        this.secretKey = key;
    }

    /**
     * 使用指定密钥构造（方便测试）。
     */
    public JwtService(String secret) {
        this.secretKey = Objects.requireNonNull(secret).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成 Access Token。
     *
     * @param username 用户名
     * @param role     角色
     * @return JWT Access Token
     */
    public String generateAccessToken(String username, Role role) {
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", username);
        payload.put("role", role.name());
        payload.put("iat", now);
        payload.put("exp", now + ACCESS_TOKEN_VALIDITY_MS);
        payload.put("type", "access");
        return buildToken(payload);
    }

    /**
     * 生成 Refresh Token。
     *
     * @param username 用户名
     * @return Refresh Token 字符串
     */
    public String generateRefreshToken(String username) {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String token = URL_ENCODER.encodeToString(randomBytes);

        long now = System.currentTimeMillis();
        refreshTokenStore.put(token, new RefreshTokenInfo(username, now + REFRESH_TOKEN_VALIDITY_MS));
        return token;
    }

    /**
     * 验证 Access Token 并返回 Claims。
     *
     * @param token JWT Token
     * @return Claims Map，验证失败返回 null
     */
    public Map<String, Object> validateAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            // 验证签名
            String expectedSignature = parts[2];
            String actualSignature = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(
                URL_DECODER.decode(expectedSignature),
                URL_DECODER.decode(actualSignature))) {
                return null;
            }

            // 解析 payload
            String payloadJson = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = parseSimpleJson(payloadJson);

            // 验证过期
            Long exp = toLong(claims.get("exp"));
            if (exp == null || exp < System.currentTimeMillis()) return null;

            // 验证类型
            if (!"access".equals(claims.get("type"))) return null;

            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 Refresh Token。
     *
     * @param token Refresh Token
     * @return 用户名，验证失败返回 null
     */
    public String validateRefreshToken(String token) {
        RefreshTokenInfo info = refreshTokenStore.get(token);
        if (info == null) return null;
        if (info.expiresAt < System.currentTimeMillis()) {
            refreshTokenStore.remove(token);
            return null;
        }
        return info.username;
    }

    /**
     * 刷新 Token 对 — 使用 Refresh Token 换取新的 Access Token + 新的 Refresh Token。
     *
     * @param refreshToken Refresh Token
     * @param roleProvider 根据用户名获取角色的函数
     * @return 新的 Token 对 (accessToken, refreshToken)，失败返回 null
     */
    public TokenPair refreshTokens(String refreshToken, java.util.function.Function<String, Role> roleProvider) {
        String username = validateRefreshToken(refreshToken);
        if (username == null) return null;

        // 移除旧 refresh token
        refreshTokenStore.remove(refreshToken);

        // 获取角色
        Role role = roleProvider.apply(username);
        if (role == null) return null;

        // 生成新 token
        String newAccessToken = generateAccessToken(username, role);
        String newRefreshToken = generateRefreshToken(username);

        return new TokenPair(newAccessToken, newRefreshToken);
    }

    /**
     * 撤销 Refresh Token。
     */
    public void revokeRefreshToken(String token) {
        refreshTokenStore.remove(token);
    }

    /**
     * 从 token 获取用户名。
     */
    public String getUsernameFromToken(String token) {
        Map<String, Object> claims = validateAccessToken(token);
        return claims != null ? (String) claims.get("sub") : null;
    }

    /**
     * 从 token 获取角色。
     */
    public Role getRoleFromToken(String token) {
        Map<String, Object> claims = validateAccessToken(token);
        if (claims == null) return null;
        try {
            return Role.valueOf((String) claims.get("role"));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 内部方法 ====================

    private String buildToken(Map<String, Object> payload) {
        // Header
        String header = URL_ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        // Payload
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append("\"").append(val).append("\"");
            } else {
                sb.append(val);
            }
            first = false;
        }
        sb.append("}");
        String payloadEncoded = URL_ENCODER.encodeToString(
            sb.toString().getBytes(StandardCharsets.UTF_8));

        // Signature
        String signature = sign(header + "." + payloadEncoded);

        return header + "." + payloadEncoded + "." + signature;
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            return URL_ENCODER.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign token", e);
        }
    }

    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> result = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().replace("\"", "");
            String value = kv[1].trim();

            if (value.startsWith("\"") && value.endsWith("\"")) {
                result.put(key, value.substring(1, value.length() - 1));
            } else {
                try {
                    result.put(key, Long.parseLong(value));
                } catch (NumberFormatException e) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    private Long toLong(Object val) {
        if (val instanceof Long) return (Long) val;
        if (val instanceof Number) return ((Number) val).longValue();
        return null;
    }

    // ==================== 内部类 ====================

    private static class RefreshTokenInfo {
        final String username;
        final long expiresAt;

        RefreshTokenInfo(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Token 对 — Access Token + Refresh Token。
     */
    public record TokenPair(String accessToken, String refreshToken) {}
}
