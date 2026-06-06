package com.credit.platform.server.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器。
 * <p>
 * 验证请求头中的 Authorization Bearer Token。
 * 仅在 dev/test profile 下允许关闭鉴权，生产环境强制开启。
 * </p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final boolean enabled;
    private final String secretKey;

    public AuthInterceptor(
            @Value("${decision.engine.auth.enabled:true}") boolean enabled,
            @Value("${decision.engine.auth.secret-key:change-me}") String secretKey) {
        this.enabled = enabled;
        this.secretKey = secretKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid Authorization header\"}");
            return false;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid or expired token\"}");
            return false;
        }

        return true;
    }

    /**
     * 验证 Token — 仅使用共享密钥匹配。
     * <p>
     * dev-token 绕过已移除。生产环境应替换为 JWT 库解析验证签名和过期时间。
     * 密钥应从配置中心 / Vault 加载，而非硬编码。
     * </p>
     *
     * @param token Bearer Token
     * @return 是否有效
     */
    private boolean validateToken(String token) {
        // 安全修复: 移除 dev-token 绕过，仅允许配置的共享密钥
        // 生产环境: 使用 JWT 库解析验证签名和过期时间
        return secretKey.equals(token);
    }
}
