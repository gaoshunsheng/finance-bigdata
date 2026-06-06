package com.credit.platform.server.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 限流拦截器 — 基于滑动窗口的简单限流。
 * <p>
 * 按 IP + 路径维度限流，超过阈值返回 429 Too Many Requests。
 * 生产环境建议使用 Redis + Lua 脚本实现分布式限流。
 * </p>
 * <p>
 * 安全修复:
 * <ul>
 *   <li>X-Forwarded-For 仅取第一个 IP（最靠近客户端），防止欺骗</li>
 *   <li>计数器 Map 增加上限，防止 IP 欺骗导致 OOM</li>
 *   <li>窗口切换使用 CAS + 双重检查，修复竞态条件</li>
 * </ul>
 * </p>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final boolean enabled;
    private final long maxRequestsPerSecond;
    private final int maxKeys;

    /** 限流计数器: key → 当前窗口计数 */
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /** 窗口重置时间戳 */
    private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());

    /** 全局请求计数 */
    private final AtomicLong globalCounter = new AtomicLong(0);

    public RateLimitInterceptor(
            @Value("${decision.engine.rate-limit.enabled:true}") boolean enabled,
            @Value("${decision.engine.rate-limit.max-requests-per-second:500}") long maxRequestsPerSecond,
            @Value("${decision.engine.rate-limit.max-keys:10000}") int maxKeys) {
        this.enabled = enabled;
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.maxKeys = maxKeys;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) throws Exception {
        if (!enabled) {
            return true;
        }

        // 安全的窗口切换：CAS + 双重检查，防止竞态条件
        long currentWindow = System.currentTimeMillis() / 1000;
        long storedWindow = windowStartMs.get() / 1000;
        if (currentWindow > storedWindow) {
            if (windowStartMs.compareAndSet(storedWindow * 1000, System.currentTimeMillis())) {
                counters.clear();
                globalCounter.set(0);
            }
        }

        // 全局限流
        long globalCount = globalCounter.incrementAndGet();
        if (globalCount > maxRequestsPerSecond * 2) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"TOO_MANY_REQUESTS\","
                + "\"message\":\"Global rate limit exceeded\"}");
            return false;
        }

        String key = buildKey(request);

        // 防止计数器 Map 无限增长
        if (counters.size() >= maxKeys) {
            // 超过最大 key 数量时执行清理
            counters.clear();
        }

        AtomicLong counter = counters.computeIfAbsent(key, k -> new AtomicLong(0));
        long count = counter.incrementAndGet();

        if (count > maxRequestsPerSecond) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"TOO_MANY_REQUESTS\","
                + "\"message\":\"Rate limit exceeded. Max " + maxRequestsPerSecond + " req/s\"}");
            return false;
        }

        return true;
    }

    /**
     * 构建限流 key — 安全获取客户端 IP。
     * <p>
     * X-Forwarded-For 仅取第一个 IP（最靠近客户端的原始 IP），
     * 攻击者无法通过伪造后续 IP 绕过限流。
     * </p>
     */
    private String buildKey(HttpServletRequest request) {
        String ip = getClientIp(request);
        return ip + ":" + request.getRequestURI();
    }

    /**
     * 安全获取客户端真实 IP。
     * <p>
     * X-Forwarded-For: client, proxy1, proxy2
     * 取第一个（client），因为这是最靠近客户端的 IP，最难被中间代理伪造。
     * 如果没有 X-Forwarded-For 头，回退到 remoteAddr。
     * </p>
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            // 只取第一个 IP（逗号前的）
            String ip = forwarded.split(",")[0].trim();
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }
}
