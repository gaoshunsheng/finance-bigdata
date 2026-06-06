package com.credit.platform.data.service.service;

import com.credit.platform.data.service.model.CustomerFeatures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 特征查询服务 — 实现 Redis -> HBase 二级查询策略。
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

    private final StringRedisTemplate stringRedisTemplate;
    private final Connection hbaseConnection;
    private final ObjectMapper objectMapper;

    public FeatureQueryService(StringRedisTemplate stringRedisTemplate,
                               Connection hbaseConnection,
                               ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.hbaseConnection = hbaseConnection;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询客户全部实时特征。
     *
     * @param customerId 客户 ID
     * @return 客户特征数据
     */
    public CustomerFeatures queryFeatures(String customerId) {
        long start = System.currentTimeMillis();

        Map<String, Object> features = new LinkedHashMap<>();
        String[] featureTypes = {"credit_query_3m", "overdue_6m", "apply_freq_1m",
                "transaction_summary_1h", "credit_score", "risk_level"};

        for (String featureType : featureTypes) {
            Map<String, Object> feature = querySingleFeature(featureType, customerId);
            if (feature != null) {
                features.putAll(feature);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        String source = features.isEmpty() ? "MISS" : "HIT";
        log.info("特征查询完成: customerId={}, 特征数={}, 耗时={}ms, 来源={}",
                customerId, features.size(), elapsed, source);

        return new CustomerFeatures(customerId, features, source, elapsed);
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
        for (String featureType : featureKeys) {
            Map<String, Object> feature = querySingleFeature(featureType, customerId);
            if (feature != null) {
                features.putAll(feature);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        String source = features.isEmpty() ? "MISS" : "HIT";
        return new CustomerFeatures(customerId, features, source, elapsed);
    }

    /**
     * 查询单个特征类型 — 先 Redis，后 HBase。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> querySingleFeature(String featureType, String customerId) {
        // 1. 尝试 Redis
        try {
            String key = "feature:" + featureType + ":" + customerId;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return objectMapper.readValue(value, Map.class);
            }
        } catch (Exception e) {
            log.warn("Redis 查询失败，回退到 HBase: featureType={}, customerId={}, error={}",
                    featureType, customerId, e.getMessage());
        }

        // 2. 尝试 HBase
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf("customer_feature"));
            // Scan latest by prefix: reversed(customerId)_featureType_
            String prefix = new StringBuilder(customerId).reverse().toString()
                    + "_" + featureType + "_";
            org.apache.hadoop.hbase.client.Scan scan = new org.apache.hadoop.hbase.client.Scan();
            byte[] prefixBytes = Bytes.toBytes(prefix);
            scan.setRowPrefixFilter(prefixBytes);
            scan.setReversed(true);
            scan.setLimit(1);
            scan.addColumn(Bytes.toBytes("cf"), Bytes.toBytes("value"));

            org.apache.hadoop.hbase.client.ResultScanner scanner = table.getScanner(scan);
            Result result = scanner.next();
            scanner.close();
            table.close();

            if (result != null && !result.isEmpty()) {
                String json = Bytes.toString(result.getValue(Bytes.toBytes("cf"), Bytes.toBytes("value")));
                if (json != null) {
                    Map<String, Object> feature = objectMapper.readValue(json, Map.class);
                    // 回写 Redis 缓存（TTL 1 小时）
                    try {
                        String key = "feature:" + featureType + ":" + customerId;
                        stringRedisTemplate.opsForValue().set(key, json, java.time.Duration.ofHours(1));
                    } catch (Exception e) {
                        log.warn("Redis 回写失败: {}", e.getMessage());
                    }
                    return feature;
                }
            }
        } catch (Exception e) {
            log.warn("HBase 查询失败: featureType={}, customerId={}, error={}",
                    featureType, customerId, e.getMessage());
        }

        return null;
    }
}
