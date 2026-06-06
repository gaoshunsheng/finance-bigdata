package com.credit.platform.admin.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * 安全修复:
 * <ul>
 *   <li>密钥从配置加载，多实例共享，不再每次重启随机生成</li>
 *   <li>JSON 构建使用正确转义，防止 JSON 注入</li>
 *   <li>Refresh Token 定时清理过期条目，防止内存泄漏</li>
 * </ul>
 * </p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long ACCESS_TOKEN_VALIDITY_MS = 2 * 60 * 60 * 1000L;   // 2 hours
    private static final long REFRESH_TOKEN_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    /** HMAC 签名密钥 — 从配置读取，确保多实例共享 */
    private final byte[] secretKey;
    /** Refresh Token 存储: token → info */
    private final ConcurrentHashMap<String, RefreshTokenInfo> refreshTokenStore = new ConcurrentHashMap<>();

    /** 过期 Token 清理调度器 */
    private final ScheduledExecutorService cleanupScheduler;

    /**
     * 生产构造函数 — 从 Spring 配置加载密钥。
     * 配置项: decision.security.jwt.secret-key (至少 32 字符)
     */
    public JwtService(@Value("${decision.security.jwt.secret-key:}") String configuredSecret) {
        if (configuredSecret != null && !configuredSecret.isEmpty()) {
            // 使用配置的密钥，确保多实例共享
            if (configuredSecret.length() < 32) {
                log.warn("JWT 密钥长度不足 32 字符，建议使用更强的密钥");
            }
            this.secretKey = configuredSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            // 仅在未配置时使用随机密钥（开发/测试场景），并输出警告
            log.warn("未配置 decision.security.jwt.secret-key，使用随机密钥（重启后所有 token 失效，多实例不共享）");
            byte[] key = new byte[64];
            new SecureRandom().nextBytes(key);
            this.secretKey = key;
        }

        // 定时清理过期的 Refresh Token（每小时一次）
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jwt-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredTokens, 1, 1, TimeUnit.HOURS);
    }

    /**
     * 生成 Access Token。
     */
    public String generateAccessToken(String username, Role role) {
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("role", role.name());
        payload.put("iat", now);
        payload.put("exp", now + ACCESS_TOKEN_VALIDITY_MS);
        payload.put("type", "access");
        return buildToken(payload);
    }

    /**
     * 生成 Refresh Token。
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
     */
    public Map<String, Object> validateAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            // 验证签名 — 使用常量时间比较防止时序攻击
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

    /**
     * 构建 JWT Token — 使用安全的 JSON 转义。
     */
    private String buildToken(Map<String, Object> payload) {
        // Header
        String header = URL_ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        // Payload — 使用安全转义防止 JSON 注入
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJsonString(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append("\"").append(escapeJsonString((String) val)).append("\"");
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

    /**
     * 安全转义 JSON 字符串值 — 处理引号、反斜杠和控制字符。
     */
    private String escapeJsonString(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
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

    /**
     * 简单 JSON 解析器 — 处理转义引号。
     */
    private Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        // 安全分割：不切割转义引号内的逗号
        List<String> pairs = splitJsonPairs(json);
        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx < 0) continue;
            String key = pair.substring(0, colonIdx).trim().replace("\"", "");
            String value = pair.substring(colonIdx + 1).trim();

            if (value.startsWith("\"") && value.endsWith("\"")) {
                result.put(key, unescapeJsonString(value.substring(1, value.length() - 1)));
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

    /**
     * 安全分割 JSON 键值对 — 不切割引号内的逗号。
     */
    private List<String> splitJsonPairs(String json) {
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                current.append(c);
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                current.append(c);
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (c == ',' && !inString) {
                pairs.add(current.toString());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            pairs.add(current.toString());
        }
        return pairs;
    }

    /**
     * 反转义 JSON 字符串。
     */
    private String unescapeJsonString(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'b' -> { sb.append('\b'); i++; }
                    case 'f' -> { sb.append('\f'); i++; }
                    case 'u' -> {
                        if (i + 5 < value.length()) {
                            String hex = value.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 清理过期的 Refresh Token。
     */
    private void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var iter = refreshTokenStore.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (entry.getValue().expiresAt < now) {
                iter.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("清理过期 Refresh Token: {} 条", removed);
        }
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
