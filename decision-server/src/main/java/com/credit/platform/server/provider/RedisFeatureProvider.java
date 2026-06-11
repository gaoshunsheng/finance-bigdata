package com.credit.platform.server.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.credit.platform.server.config.FeatureProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis 特征读取器 — 从 Redis 缓存读取 Flink 预计算特征。
 *
 * <p>Key 格式: {@code feature:{featureType}:{customerId}}
 * <p>作为 {@link CachedVariableProvider} 的第一级缓存。
 *
 * <p>批量读取使用 {@code multiGet} 减少网络往返次数。
 */
public class RedisFeatureProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisFeatureProvider.class);

    private static final String KEY_PREFIX = "feature:";

    private final StringRedisTemplate redisTemplate;
    private final FeatureProperties properties;
    private final ObjectMapper objectMapper;

    public RedisFeatureProvider(StringRedisTemplate redisTemplate,
                                FeatureProperties properties,
                                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 从 Redis 批量查询特征值。
     *
     * @param varIds      需要查询的变量 ID 集合
     * @param customerId  客户 ID（从上下文获取）
     * @return 变量 ID → 变量值映射（仅包含 Redis 命中的变量）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> query(Set<String> varIds, String customerId) {
        if (varIds == null || varIds.isEmpty() || customerId == null) {
            return Map.of();
        }

        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();

        try {
            // 构建 Redis Key 列表和 Key→varId 映射
            List<String> keys = new ArrayList<>();
            Map<String, String> keyToVarId = new HashMap<>();
            for (String varId : varIds) {
                String featureType = mapVarIdToFeatureType(varId);
                if (featureType != null) {
                    String key = KEY_PREFIX + featureType + ":" + customerId;
                    keys.add(key);
                    keyToVarId.put(key, varId);
                }
            }

            if (keys.isEmpty()) {
                return Map.of();
            }

            // 批量读取
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }

            // 解析结果
            for (int i = 0; i < keys.size(); i++) {
                String value = values.get(i);
                if (value != null) {
                    String key = keys.get(i);
                    String varId = keyToVarId.get(key);
                    try {
                        Map<String, Object> featureMap = objectMapper.readValue(value, Map.class);
                        Object extracted = extractFeatureValue(featureMap, varId);
                        if (extracted != null) {
                            result.put(varId, extracted);
                        }
                    } catch (Exception e) {
                        log.debug("Redis 值解析失败: key={}, error={}", key, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Redis 批量查询异常: customerId={}, error={}", customerId, e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.debug("Redis 特征查询: customerId={}, 请求={}, 命中={}, 耗时={}ms",
            customerId, varIds.size(), result.size(), elapsed);

        return result;
    }

    /**
     * 将变量 ID 映射为 Redis Key 中的 featureType。
     *
     * <p>映射规则:
     * <ul>
     *   <li>var_credit_query_count_3m → credit_query_3m</li>
     *   <li>var_overdue_count_6m → overdue_6m</li>
     * </ul>
     */
    private String mapVarIdToFeatureType(String varId) {
        // 去掉 var_ 前缀后尝试匹配
        String normalized = varId.startsWith("var_") ? varId.substring(4) : varId;

        for (String featureType : properties.getCache().getFeatureTypes()) {
            if (normalized.equals(featureType) || normalized.startsWith(featureType.split("_")[0])) {
                return featureType;
            }
        }

        // 直接使用变量 ID 作为 featureType（兼容自定义映射）
        return normalized;
    }

    /**
     * 从特征 JSON 中提取变量值。
     */
    private Object extractFeatureValue(Map<String, Object> featureMap, String varId) {
        if (featureMap.isEmpty()) {
            return null;
        }
        // 尝试精确匹配
        if (featureMap.containsKey(varId)) {
            return featureMap.get(varId);
        }
        // 尝试去掉 var_ 前缀匹配
        String stripped = varId.startsWith("var_") ? varId.substring(4) : varId;
        if (featureMap.containsKey(stripped)) {
            return featureMap.get(stripped);
        }
        // 如果有 value 字段，取 value
        if (featureMap.containsKey("value")) {
            return featureMap.get("value");
        }
        // 返回整个特征 Map（让上层决定如何使用）
        return featureMap;
    }

    /**
     * 回写 Redis 缓存。
     */
    public void writeBack(String featureType, String customerId, String json) {
        try {
            String key = KEY_PREFIX + featureType + ":" + customerId;
            redisTemplate.opsForValue().set(key, json,
                java.time.Duration.ofHours(properties.getCache().getRedisTtlHours()));
            log.debug("Redis 回写: key={}, ttl={}h", key, properties.getCache().getRedisTtlHours());
        } catch (Exception e) {
            log.warn("Redis 回写失败: featureType={}, customerId={}, error={}",
                featureType, customerId, e.getMessage());
        }
    }
}
