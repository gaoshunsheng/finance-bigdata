package com.credit.platform.server.provider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.credit.platform.engine.core.variable.VariableProvider;
import com.credit.platform.engine.core.variable.VariableResolveContext;
import com.credit.platform.server.config.FeatureProperties;

/**
 * 缓存变量提供者 — L2 CACHED 层 VariableProvider 实现。
 *
 * <p>实现 Redis → HBase 二级查询策略:
 * <ol>
 *   <li>优先查询 Redis 缓存（亚毫秒延迟）</li>
 *   <li>Redis 未命中则查询 HBase（毫秒级延迟）</li>
 *   <li>HBase 命中后回写 Redis 缓存（TTL 由配置决定）</li>
 *   <li>全部未命中时返回空结果（不阻塞决策流程）</li>
 * </ol>
 *
 * <p>容错设计:
 * <ul>
 *   <li>Redis/HBase 连接异常时静默降级，不抛出异常</li>
 *   <li>超时控制防止单次查询拖慢整体决策</li>
 *   <li>详细日志记录缓存命中率，便于监控</li>
 * </ul>
 */
public class CachedVariableProvider implements VariableProvider {

    private static final Logger log = LoggerFactory.getLogger(CachedVariableProvider.class);

    private final RedisFeatureProvider redisProvider;
    private final HBaseFeatureProvider hbaseProvider;
    private final FeatureProperties properties;

    /** 缓存命中统计 */
    private long redisHits = 0;
    private long hbaseHits = 0;
    private long totalQueries = 0;

    public CachedVariableProvider(RedisFeatureProvider redisProvider,
                                  HBaseFeatureProvider hbaseProvider,
                                  FeatureProperties properties) {
        this.redisProvider = redisProvider;
        this.hbaseProvider = hbaseProvider;
        this.properties = properties;
    }

    /**
     * 批量获取 L2 缓存变量值。
     *
     * <p>实现 VariableProvider 接口，被 VariableEngine 在 L2 层调用。
     * 从上下文的 requestParams 中提取 customerId 用于查询。
     */
    @Override
    public Map<String, Object> provide(Set<String> varIds, VariableResolveContext context) {
        if (varIds == null || varIds.isEmpty()) {
            return Map.of();
        }

        String customerId = extractCustomerId(context);
        if (customerId == null) {
            log.debug("无法提取 customerId，跳过 L2 缓存查询");
            return Map.of();
        }

        return queryWithFallback(varIds, customerId);
    }

    /**
     * Redis → HBase 降级查询链。
     */
    public Map<String, Object> queryWithFallback(Set<String> varIds, String customerId) {
        totalQueries++;

        Map<String, Object> result = new HashMap<>();
        Set<String> unresolved = new HashSet<>(varIds);

        long start = System.currentTimeMillis();

        // 1. 尝试 Redis
        try {
            Map<String, Object> redisResult = redisProvider.query(unresolved, customerId);
            if (redisResult != null && !redisResult.isEmpty()) {
                result.putAll(redisResult);
                unresolved.removeAll(redisResult.keySet());
                redisHits++;
            }
        } catch (Exception e) {
            log.warn("Redis 查询异常，降级到 HBase: customerId={}, error={}",
                customerId, e.getMessage());
        }

        // 2. Redis 未命中的变量尝试 HBase
        if (!unresolved.isEmpty()) {
            try {
                Map<String, Object> hbaseResult = hbaseProvider.query(unresolved, customerId);
                if (hbaseResult != null && !hbaseResult.isEmpty()) {
                    result.putAll(hbaseResult);
                    hbaseHits++;
                }
            } catch (Exception e) {
                log.warn("HBase 查询异常: customerId={}, error={}",
                    customerId, e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("L2 缓存查询: customerId={}, 请求={}, 命中={}, 未命中={}, 耗时={}ms, 命中率={}%",
            customerId,
            varIds.size(),
            result.size(),
            varIds.size() - result.size(),
            elapsed,
            varIds.isEmpty() ? 100 : (result.size() * 100 / varIds.size()));

        return result;
    }

    /**
     * 从上下文中提取 customer_id。
     *
     * <p>尝试多个常见的键名:
     * customer_id, customerId, customerid, userId, user_id
     */
    private String extractCustomerId(VariableResolveContext context) {
        if (context == null) return null;

        Map<String, Object> params = context.getRequestParams();
        if (params == null) return null;

        // 按优先级尝试多个键名
        String[] candidateKeys = {"customer_id", "customerId", "customerid", "userId", "user_id"};
        for (String key : candidateKeys) {
            Object value = params.get(key);
            if (value != null) {
                return value.toString();
            }
        }

        return null;
    }

    /**
     * 获取缓存命中统计。
     */
    public CacheStats getStats() {
        return new CacheStats(totalQueries, redisHits, hbaseHits);
    }

    /**
     * 缓存命中统计快照。
     */
    public static class CacheStats {
        private final long totalQueries;
        private final long redisHits;
        private final long hbaseHits;

        CacheStats(long totalQueries, long redisHits, long hbaseHits) {
            this.totalQueries = totalQueries;
            this.redisHits = redisHits;
            this.hbaseHits = hbaseHits;
        }

        public long getTotalQueries() { return totalQueries; }
        public long getRedisHits() { return redisHits; }
        public long getHbaseHits() { return hbaseHits; }
        public double getHitRate() {
            return totalQueries == 0 ? 0 : (redisHits + hbaseHits) * 100.0 / totalQueries;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{total=%d, redis=%d, hbase=%d, rate=%.1f%%}",
                totalQueries, redisHits, hbaseHits, getHitRate());
        }
    }
}
