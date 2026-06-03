package com.credit.platform.engine.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 规则缓存管理器。
 * <p>
 * 使用 Caffeine 本地缓存存储编译后的规则、流程和评分卡。
 * 支持 CopyOnWrite 语义：更新缓存时旧请求继续使用旧版本，直到自然过期。
 * </p>
 *
 * <h3>缓存分区</h3>
 * <ul>
 *   <li><b>ruleCache</b> — 单条规则，最大 10,000 条，写入后 24 小时过期</li>
 *   <li><b>flowCache</b> — 决策流程，最大 1,000 条，写入后 24 小时过期</li>
 *   <li><b>scorecardCache</b> — 评分卡，最大 1,000 条，写入后 24 小时过期</li>
 * </ul>
 *
 * <pre>
 * RuleCacheManager cacheManager = new RuleCacheManager();
 * cacheManager.putRule("rule-001", ruleObject);
 * Object rule = cacheManager.getRule("rule-001");
 * </pre>
 */
public class RuleCacheManager {

    private final Cache<String, Object> ruleCache;
    private final Cache<String, Object> flowCache;
    private final Cache<String, Object> scorecardCache;

    /**
     * 构造函数，初始化三个缓存分区。
     */
    public RuleCacheManager() {
        this.ruleCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();
        this.flowCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();
        this.scorecardCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();
    }

    // ==================== Rule Cache ====================

    /**
     * 获取规则。
     *
     * @param key 规则键
     * @return 规则对象，缓存未命中时返回 {@code null}
     */
    public Object getRule(String key) {
        return ruleCache.getIfPresent(key);
    }

    /**
     * 存入规则。
     *
     * @param key   规则键
     * @param value 规则对象
     */
    public void putRule(String key, Object value) {
        ruleCache.put(key, value);
    }

    // ==================== Flow Cache ====================

    /**
     * 获取决策流程。
     *
     * @param key 流程键
     * @return 流程对象，缓存未命中时返回 {@code null}
     */
    public Object getFlow(String key) {
        return flowCache.getIfPresent(key);
    }

    /**
     * 存入决策流程。
     *
     * @param key   流程键
     * @param value 流程对象
     */
    public void putFlow(String key, Object value) {
        flowCache.put(key, value);
    }

    // ==================== Scorecard Cache ====================

    /**
     * 获取评分卡。
     *
     * @param key 评分卡键
     * @return 评分卡对象，缓存未命中时返回 {@code null}
     */
    public Object getScorecard(String key) {
        return scorecardCache.getIfPresent(key);
    }

    /**
     * 存入评分卡。
     *
     * @param key   评分卡键
     * @param value 评分卡对象
     */
    public void putScorecard(String key, Object value) {
        scorecardCache.put(key, value);
    }

    // ==================== Invalidation ====================

    /**
     * 失效指定缓存分区中的某条记录。
     *
     * @param cacheType 缓存分区类型：{@code "rule"}、{@code "flow"} 或 {@code "scorecard"}
     * @param key       要失效的键
     */
    public void invalidate(String cacheType, String key) {
        switch (cacheType) {
            case "rule":
                ruleCache.invalidate(key);
                break;
            case "flow":
                flowCache.invalidate(key);
                break;
            case "scorecard":
                scorecardCache.invalidate(key);
                break;
            default:
                throw new IllegalArgumentException("Unknown cache type: " + cacheType);
        }
    }

    /**
     * 清除所有缓存分区的全部记录。
     */
    public void invalidateAll() {
        ruleCache.invalidateAll();
        flowCache.invalidateAll();
        scorecardCache.invalidateAll();
    }

    /**
     * 获取各缓存分区的统计信息。
     *
     * @return 统计信息 Map，包含各分区的 hitRate、evictionCount、size 等
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("rule", buildCacheStats(ruleCache));
        stats.put("flow", buildCacheStats(flowCache));
        stats.put("scorecard", buildCacheStats(scorecardCache));
        return stats;
    }

    private Map<String, Object> buildCacheStats(Cache<String, Object> cache) {
        Map<String, Object> result = new HashMap<>();
        var stats = cache.stats();
        result.put("hitRate", stats.hitRate());
        result.put("hitCount", stats.hitCount());
        result.put("missCount", stats.missCount());
        result.put("evictionCount", stats.evictionCount());
        result.put("size", cache.estimatedSize());
        return result;
    }
}
