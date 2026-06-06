package com.credit.platform.server.adapter;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 外部 API 适配器抽象基类 — 提供超时/重试/降级/Mock 的通用模板。
 * <p>
 * 子类只需实现：
 * <ul>
 *   <li>{@link #doFetch(Set, Map)} — 真实的 API 调用逻辑</li>
 *   <li>{@link #doFetchMock(Set, Map)} — Mock 模式的模拟数据</li>
 * </ul>
 * </p>
 *
 * <p>模板方法流程：</p>
 * <pre>
 * fetch(varIds, ctx)
 *  ├─ Mock 模式? → doFetchMock() → return
 *  ├─ 检查缓存 → return cached
 *  ├─ for i in 0..retryCount:
 *  │   ├─ doFetch() with timeout
 *  │   ├─ 成功 → 更新缓存 → return
 *  │   └─ 失败 → 继续重试
 *  └─ 全部失败 → 降级到缓存 → return
 * </pre>
 */
public abstract class AbstractExternalApiAdapter implements ExternalApiAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 降级缓存：varId → (value, timestamp) */
    private final ConcurrentHashMap<String, CacheEntry> fallbackCache = new ConcurrentHashMap<>();

    /** 最近一次调用耗时 */
    private volatile long lastCallDurationMs = 0;

    /** 健康状态 */
    private volatile boolean healthy = true;

    /** 连续失败计数，用于熔断 */
    private volatile int consecutiveFailures = 0;

    /** 熔断阈值：连续失败 N 次后标记为不健康 */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

    protected final ExternalApiProperties properties;

    protected AbstractExternalApiAdapter(ExternalApiProperties properties) {
        this.properties = properties;
    }

    // ========== 子类必须实现 ==========

    /**
     * 执行真实的外部 API 调用。
     *
     * @param varIds      需要获取的变量 ID
     * @param contextVars 上下文变量
     * @return 变量名 → 变量值
     */
    protected abstract Map<String, Object> doFetch(Set<String> varIds, Map<String, Object> contextVars);

    /**
     * Mock 模式：返回模拟数据。
     *
     * @param varIds      需要获取的变量 ID
     * @param contextVars 上下文变量
     * @return 模拟的变量名 → 变量值
     */
    protected abstract Map<String, Object> doFetchMock(Set<String> varIds, Map<String, Object> contextVars);

    // ========== 模板方法 ==========

    @Override
    public final Map<String, Object> fetch(Set<String> varIds, Map<String, Object> contextVars) {
        if (varIds == null || varIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 全局开关检查
        if (!properties.isEnabled()) {
            log.debug("[{}] External API disabled globally, returning cached/mock data", getAdapterType());
            return fallback(varIds, contextVars);
        }

        // 2. 适配器级别开关检查
        ExternalApiProperties.AdapterConfig config = properties.getAdapterConfig(getAdapterType());
        if (config != null && !config.isEnabled()) {
            log.debug("[{}] Adapter disabled, returning cached/mock data", getAdapterType());
            return fallback(varIds, contextVars);
        }

        // 3. Mock 模式检查（全局或适配器级别）
        boolean useMock = properties.isMockMode() || (config != null && config.isMockMode());
        if (useMock) {
            log.debug("[{}] Mock mode enabled, returning mock data", getAdapterType());
            return doFetchMock(varIds, contextVars);
        }

        // 4. 熔断检查
        if (!healthy) {
            log.warn("[{}] Circuit breaker open, returning cached data", getAdapterType());
            return fallback(varIds, contextVars);
        }

        // 5. 带重试的调用
        int maxRetries = resolveRetryCount(config);
        long timeoutMs = resolveTimeoutMs(config);
        long startMs = System.currentTimeMillis();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                Map<String, Object> result = executeWithTimeout(varIds, contextVars, timeoutMs);

                // 成功：更新缓存和健康状态
                lastCallDurationMs = System.currentTimeMillis() - startMs;
                updateCache(result);
                consecutiveFailures = 0;
                healthy = true;

                log.debug("[{}] Fetched {} variables in {}ms (attempt {})",
                    getAdapterType(), result.size(), lastCallDurationMs, attempt + 1);
                return result;

            } catch (TimeoutException e) {
                log.warn("[{}] Timeout on attempt {}/{} ({}ms)",
                    getAdapterType(), attempt + 1, maxRetries + 1, timeoutMs);
            } catch (Exception e) {
                log.warn("[{}] Error on attempt {}/{}: {}",
                    getAdapterType(), attempt + 1, maxRetries + 1, e.getMessage());
            }
        }

        // 6. 所有重试失败 → 降级
        lastCallDurationMs = System.currentTimeMillis() - startMs;
        consecutiveFailures++;
        if (consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD) {
            healthy = false;
            log.error("[{}] Circuit breaker opened after {} consecutive failures",
                getAdapterType(), consecutiveFailures);
        }

        return fallback(varIds, contextVars);
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public long getLastCallDurationMs() {
        return lastCallDurationMs;
    }

    // ========== 内部方法 ==========

    /**
     * 带超时的 API 调用。
     * <p>
     * 使用 Thread + join(timeout) 实现简单超时控制，
     * 生产环境可替换为 CompletableFuture + executor。
     * </p>
     */
    private Map<String, Object> executeWithTimeout(Set<String> varIds,
                                                     Map<String, Object> contextVars,
                                                     long timeoutMs) throws TimeoutException {
        // 使用 CompletableFuture 实现超时
        try {
            return java.util.concurrent.CompletableFuture
                .supplyAsync(() -> doFetch(varIds, contextVars))
                .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException("API call timed out after " + timeoutMs + "ms");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("API call failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("API call interrupted", e);
        }
    }

    /**
     * 降级策略：先查缓存，再返回 Mock 数据。
     */
    private Map<String, Object> fallback(Set<String> varIds, Map<String, Object> contextVars) {
        Map<String, Object> result = new HashMap<>();

        // 1. 从缓存中获取
        for (String varId : varIds) {
            CacheEntry entry = fallbackCache.get(varId);
            if (entry != null && !entry.isExpired(properties.getCacheTtlMs())) {
                result.put(varId, entry.value);
            }
        }

        // 2. 缓存中没有的变量，用 Mock 补充
        Set<String> missing = new java.util.HashSet<>(varIds);
        missing.removeAll(result.keySet());
        if (!missing.isEmpty()) {
            Map<String, Object> mockData = doFetchMock(missing, contextVars);
            result.putAll(mockData);
        }

        return result;
    }

    /**
     * 更新降级缓存。
     */
    private void updateCache(Map<String, Object> result) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            fallbackCache.put(entry.getKey(), new CacheEntry(entry.getValue(), now));
        }
    }

    private int resolveRetryCount(ExternalApiProperties.AdapterConfig config) {
        if (config != null && config.getRetryCount() >= 0) return config.getRetryCount();
        return properties.getRetryCount();
    }

    private long resolveTimeoutMs(ExternalApiProperties.AdapterConfig config) {
        if (config != null && config.getTimeoutMs() > 0) return config.getTimeoutMs();
        return properties.getTimeoutMs();
    }

    // ========== 缓存条目 ==========

    private static class CacheEntry {
        final Object value;
        final long timestamp;

        CacheEntry(Object value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }
}
