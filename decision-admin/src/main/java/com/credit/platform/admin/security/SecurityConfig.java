package com.credit.platform.admin.security;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置 -JWT 无状态认证 + RBAC 端点权限。
 * <p>
 * 端点权限规则:
 * <ul>
 *   <li>POST /api/v1/auth/login, /api/v1/auth/refresh - 公开</li>
 *   <li>GET /api/v1/** - VIEWER 及以上</li>
 *   <li>POST/PUT approve, reject - APPROVER 及以上</li>
 *   <li>POST/PUT /api/v1/** - EDITOR 及以上</li>
 *   <li>DELETE /api/v1/** - ADMIN</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "decision.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公开端点
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/refresh").permitAll()
                // 用户管理 -ADMIN
                .requestMatchers("/api/v1/auth/users").hasRole("ADMIN")
                .requestMatchers("/api/v1/auth/users/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/auth/change-password").authenticated()
                // 审批操作 -APPROVER 及以上
                .requestMatchers("/api/v1/publish/**/approve").hasRole("APPROVER")
                .requestMatchers("/api/v1/publish/**/reject").hasRole("APPROVER")
                // 灰度管理 -APPROVER 及以上
                .requestMatchers("/api/v1/publish/**/grayscale/**").hasRole("APPROVER")
                // POST/PUT -EDITOR 及以上
                .requestMatchers(HttpMethod.POST, "/api/v1/**").hasRole("EDITOR")
                .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasRole("EDITOR")
                // GET -VIEWER 及以上
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasRole("VIEWER")
                // 其他
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"code\":401,\"message\":\"Unauthorized - Invalid or missing token\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"code\":403,\"message\":\"Forbidden - Insufficient permissions\"}");
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
