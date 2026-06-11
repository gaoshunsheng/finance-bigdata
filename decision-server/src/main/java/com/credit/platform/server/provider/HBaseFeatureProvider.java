package com.credit.platform.server.provider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.credit.platform.server.config.FeatureProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HBase 特征读取器 — 从 HBase 历史特征表读取数据。
 *
 * <p>表: {@code customer_feature}
 * <p>列族: {@code cf}
 * <p>RowKey: {@code {reversedCustomerId}_{featureType}_{timestamp}}
 *
 * <p>作为 {@link CachedVariableProvider} 的第二级缓存（Redis 未命中时调用）。
 * HBase 命中后会将结果回写 Redis。
 */
public class HBaseFeatureProvider {

    private static final Logger log = LoggerFactory.getLogger(HBaseFeatureProvider.class);

    private final Connection hbaseConnection;
    private final FeatureProperties properties;
    private final ObjectMapper objectMapper;
    private final RedisFeatureProvider redisFeatureProvider;

    public HBaseFeatureProvider(Connection hbaseConnection,
                                FeatureProperties properties,
                                ObjectMapper objectMapper,
                                RedisFeatureProvider redisFeatureProvider) {
        this.hbaseConnection = hbaseConnection;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisFeatureProvider = redisFeatureProvider;
    }

    /**
     * 从 HBase 批量查询特征值。
     *
     * <p>对每个变量 ID，使用前缀扫描查找最新的特征记录。
     * HBase Scan 使用 reversed scan + limit(1) 获取最新版本。
     *
     * @param varIds      需要查询的变量 ID 集合
     * @param customerId  客户 ID
     * @return 变量 ID → 变量值映射（仅包含 HBase 命中的变量）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> query(Set<String> varIds, String customerId) {
        if (varIds == null || varIds.isEmpty() || customerId == null) {
            return Map.of();
        }

        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        String tableName = properties.getHbase().getTableName();
        String cf = properties.getHbase().getColumnFamily();
        byte[] cfBytes = Bytes.toBytes(cf);

        try (Table table = hbaseConnection.getTable(TableName.valueOf(tableName))) {
            String reversedCustomerId = new StringBuilder(customerId).reverse().toString();

            for (String varId : varIds) {
                String featureType = mapVarIdToFeatureType(varId);
                if (featureType == null) continue;

                try {
                    String prefix = reversedCustomerId + "_" + featureType + "_";
                    byte[] prefixBytes = Bytes.toBytes(prefix);

                    Scan scan = new Scan();
                    scan.setRowPrefixFilter(prefixBytes);
                    scan.setReversed(true);
                    scan.setLimit(1);
                    scan.addColumn(cfBytes, Bytes.toBytes("value"));

                    try (ResultScanner scanner = table.getScanner(scan)) {
                        Result hbaseResult = scanner.next();
                        if (hbaseResult != null && !hbaseResult.isEmpty()) {
                            String json = Bytes.toString(hbaseResult.getValue(cfBytes, Bytes.toBytes("value")));
                            if (json != null) {
                                Map<String, Object> featureMap = objectMapper.readValue(json, Map.class);

                                // 提取变量值
                                Object value = extractFeatureValue(featureMap, varId);
                                if (value != null) {
                                    result.put(varId, value);

                                    // 回写 Redis 缓存
                                    redisFeatureProvider.writeBack(featureType, customerId, json);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("HBase 查询失败: varId={}, customerId={}, error={}",
                        varId, customerId, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("HBase 连接异常: table={}, error={}", tableName, e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - start;
        log.debug("HBase 特征查询: customerId={}, 请求={}, 命中={}, 耗时={}ms",
            customerId, varIds.size(), result.size(), elapsed);

        return result;
    }

    /**
     * 将变量 ID 映射为 HBase RowKey 中的 featureType。
     */
    private String mapVarIdToFeatureType(String varId) {
        String normalized = varId.startsWith("var_") ? varId.substring(4) : varId;

        for (String featureType : properties.getCache().getFeatureTypes()) {
            if (normalized.equals(featureType) || normalized.startsWith(featureType.split("_")[0])) {
                return featureType;
            }
        }

        return normalized;
    }

    /**
     * 从特征 JSON 中提取变量值。
     */
    private Object extractFeatureValue(Map<String, Object> featureMap, String varId) {
        if (featureMap.isEmpty()) {
            return null;
        }
        if (featureMap.containsKey(varId)) {
            return featureMap.get(varId);
        }
        String stripped = varId.startsWith("var_") ? varId.substring(4) : varId;
        if (featureMap.containsKey(stripped)) {
            return featureMap.get(stripped);
        }
        if (featureMap.containsKey("value")) {
            return featureMap.get("value");
        }
        return featureMap;
    }
}
