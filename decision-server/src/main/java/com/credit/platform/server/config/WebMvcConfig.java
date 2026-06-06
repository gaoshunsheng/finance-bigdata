package com.credit.platform.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.credit.platform.server.interceptor.AuthInterceptor;
import com.credit.platform.server.interceptor.DecisionAuditInterceptor;
import com.credit.platform.server.interceptor.RateLimitInterceptor;

/**
 * Web MVC 配置 — 注册拦截器。
 * <p>
 * 安全修复: 注册 DecisionAuditInterceptor，启用审计功能。
 * </p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final DecisionAuditInterceptor auditInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        RateLimitInterceptor rateLimitInterceptor,
                        DecisionAuditInterceptor auditInterceptor) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/api/v1/**")
            .order(1);

        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/api/v1/decision/health")
            .order(2);

        // 安全修复: 注册审计拦截器，仅拦截决策执行端点
        registry.addInterceptor(auditInterceptor)
            .addPathPatterns("/api/v1/decision/execute")
            .order(3);
    }
}
