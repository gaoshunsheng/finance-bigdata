package com.credit.platform.server.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 限流拦截器 — 基于滑动窗口的简单限流。
 * <p>
 * 按 IP + 路径维度限流，超过阈值返回 429 Too Many Requests。
 * 生产环境建议使用 Redis + Lua 脚本实现分布式限流。
 * </p>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final boolean enabled;
    private final long maxRequestsPerSecond;

    /** 限流计数器: key → 当前窗口计数 */
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    /** 窗口重置时间戳 */
    private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());

    public RateLimitInterceptor(
            @Value("${decision.engine.rate-limit.enabled:true}") boolean enabled,
            @Value("${decision.engine.rate-limit.max-requests-per-second:500}") long maxRequestsPerSecond) {
        this.enabled = enabled;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        String key = buildKey(request);
        long currentWindow = System.currentTimeMillis() / 1000;
        long storedWindow = windowStartMs.get() / 1000;

        // 窗口切换 — 重置计数器
        if (currentWindow > storedWindow) {
            windowStartMs.set(System.currentTimeMillis());
            counters.clear();
        }

        // 原子递增
        LongAdder adder = counters.computeIfAbsent(key, k -> new LongAdder());
        adder.increment();

        if (adder.sum() > maxRequestsPerSecond) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"TOO_MANY_REQUESTS\","
                + "\"message\":\"Rate limit exceeded. Max " + maxRequestsPerSecond + " req/s\"}");
            return false;
        }

        return true;
    }

    private String buildKey(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return ip + ":" + request.getRequestURI();
    }
}
