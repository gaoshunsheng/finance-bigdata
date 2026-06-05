package com.credit.platform.data.service.service;

import com.credit.platform.data.service.model.CustomerFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 特征查询服务 — 实现 Redis → HBase 二级查询策略。
 *
 * <p>查询策略:
 * <ol>
 *   <li>优先查询 Redis 缓存（亚毫秒延迟）</li>
 *   <li>Redis 未命中则查询 HBase（毫秒级延迟）</li>
 *   <li>查询结果回写 Redis 缓存</li>
 *   <li>目标: P99 < 20ms</li>
 * </ol>
 *
 * <p>特征 Key 命名规范: {@code feature:{featureType}:{customerId}}
 */
@Service
public class FeatureQueryService {

    private static final Logger log = LoggerFactory.getLogger(FeatureQueryService.class);

    /**
     * 查询客户全部实时特征。
     *
     * <p>当前实现为 Mock 模式（不依赖真实 Redis/HBase 连接），
     * 生产环境需替换为真实的 Redis/HBase 查询逻辑。
     *
     * @param customerId 客户 ID
     * @return 客户特征数据
     */
    public CustomerFeatures queryFeatures(String customerId) {
        long start = System.currentTimeMillis();

        Map<String, Object> features = new LinkedHashMap<>();

        // 模拟特征数据（生产环境从 Redis/HBase 查询）
        features.put("credit_query_count_3m", mockFromRedis("credit_query_3m", customerId, 5));
        features.put("overdue_count_6m", mockFromRedis("overdue_6m", customerId, 2));
        features.put("apply_freq_1m", mockFromRedis("apply_freq_1m", customerId, 1));
        features.put("transaction_summary_1h_total", mockFromHBase("transaction_summary_1h", customerId, 15000.00));
        features.put("transaction_summary_1h_count", mockFromHBase("transaction_summary_1h_count", customerId, 3));
        features.put("credit_score", mockFromHBase("credit_score", customerId, 720));
        features.put("risk_level", mockFromHBase("risk_level", customerId, "LOW"));

        long elapsed = System.currentTimeMillis() - start;
        log.info("特征查询完成: customerId={}, 特征数={}, 耗时={}ms, 来源=MOCK",
                customerId, features.size(), elapsed);

        return new CustomerFeatures(customerId, features, "MOCK", elapsed);
    }

    /**
     * 查询客户指定特征。
     *
     * @param customerId  客户 ID
     * @param featureKeys 需要查询的特征 Key 列表
     * @return 客户特征数据
     */
    public CustomerFeatures queryFeatures(String customerId, java.util.List<String> featureKeys) {
        long start = System.currentTimeMillis();

        Map<String, Object> features = new LinkedHashMap<>();
        for (String key : featureKeys) {
            features.put(key, mockFromRedis(key, customerId, 0));
        }

        long elapsed = System.currentTimeMillis() - start;
        return new CustomerFeatures(customerId, features, "MOCK", elapsed);
    }

    /** 模拟 Redis 查询 */
    private Object mockFromRedis(String featureType, String customerId, Object defaultValue) {
        // 生产环境: redisTemplate.opsForValue().get("feature:" + featureType + ":" + customerId)
        return defaultValue;
    }

    /** 模拟 HBase 查询 */
    private Object mockFromHBase(String featureType, String customerId, Object defaultValue) {
        // 生产环境: hbaseTemplate.get("customer_feature", rowKey, "cf:" + featureType)
        return defaultValue;
    }
}
