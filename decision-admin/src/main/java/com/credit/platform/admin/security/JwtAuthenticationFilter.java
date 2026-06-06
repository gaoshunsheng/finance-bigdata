package com.credit.platform.admin.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器 — 从 Authorization header 提取并验证 JWT Token。
 * <p>
 * 验证成功后设置 Spring Security Context。
 * 角色层级在认证时展开: ADMIN → APPROVER → EDITOR → VIEWER。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "decision.security.enabled", havingValue = "true", matchIfMissing = false)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Map<String, Object> claims = jwtService.validateAccessToken(token);

            if (claims != null) {
                String username = (String) claims.get("sub");
                String role = (String) claims.get("role");

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        expandRoleHierarchy(role)
                    );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 展开角色层级: ADMIN > APPROVER > EDITOR > VIEWER
     * <p>
     * 高级角色自动继承低级角色的所有权限。
     */
    private static List<SimpleGrantedAuthority> expandRoleHierarchy(String role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

        switch (role) {
            case "ADMIN":
                authorities.add(new SimpleGrantedAuthority("ROLE_APPROVER"));
                // fall through
            case "APPROVER":
                authorities.add(new SimpleGrantedAuthority("ROLE_EDITOR"));
                // fall through
            case "EDITOR":
                authorities.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
                break;
            default:
                break;
        }

        return authorities;
    }
}
