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
 * 开发阶段支持通过配置关闭鉴权。
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
     * 验证 Token。
     * <p>
     * 当前实现为简单密钥匹配，生产环境应替换为 JWT 解析验证。
     * </p>
     *
     * @param token Bearer Token
     * @return 是否有效
     */
    private boolean validateToken(String token) {
        // 开发阶段: 简单密钥匹配
        // 生产环境: 使用 JWT 库解析验证签名和过期时间
        return secretKey.equals(token) || isValidDevToken(token);
    }

    /**
     * 开发令牌格式: dev-{任意字符串}
     * 生产环境应移除此方法。
     */
    private boolean isValidDevToken(String token) {
        return token != null && token.startsWith("dev-");
    }
}
