package com.credit.platform.data.service.model;

import java.util.Map;

/**
 * 客户特征数据模型 — 包含从 Redis/HBase 查询到的客户实时特征
 */
public class CustomerFeatures {

    private String customerId;
    private Map<String, Object> features;
    private String source; // REDIS / HBASE / COMPUTED
    private long queryTimeMs;

    public CustomerFeatures() {
    }

    public CustomerFeatures(String customerId, Map<String, Object> features,
                            String source, long queryTimeMs) {
        this.customerId = customerId;
        this.features = features;
        this.source = source;
        this.queryTimeMs = queryTimeMs;
    }

    // Getters and Setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public Map<String, Object> getFeatures() { return features; }
    public void setFeatures(Map<String, Object> features) { this.features = features; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public long getQueryTimeMs() { return queryTimeMs; }
    public void setQueryTimeMs(long queryTimeMs) { this.queryTimeMs = queryTimeMs; }
}
