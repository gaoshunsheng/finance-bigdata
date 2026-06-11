package com.credit.platform.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 特征存储配置 — Redis/HBase 连接参数和 Key 命名规则。
 *
 * <p>对应 application.yml 中 {@code feature.store.*} 前缀。
 *
 * <p>Key 命名规范:
 * <ul>
 *   <li>Redis Key: {@code feature:{featureType}:{customerId}}</li>
 *   <li>HBase RowKey: {@code {reversedCustomerId}_{featureType}_{timestamp}}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "feature.store")
public class FeatureProperties {

    /** 是否启用 L2 缓存变量提供者 */
    private boolean enabled = false;

    /** Redis 配置 */
    private Redis redis = new Redis();

    /** HBase 配置 */
    private HBase hbase = new HBase();

    /** 缓存配置 */
    private Cache cache = new Cache();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }

    public HBase getHbase() { return hbase; }
    public void setHbase(HBase hbase) { this.hbase = hbase; }

    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }

    /**
     * Redis 连接配置。
     */
    public static class Redis {
        /** Redis 主机 */
        private String host = "localhost";
        /** Redis 端口 */
        private int port = 6379;
        /** Redis 密码 */
        private String password;
        /** Redis 数据库 */
        private int database = 0;
        /** 连接超时 (ms) */
        private int timeoutMs = 3000;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public int getDatabase() { return database; }
        public void setDatabase(int database) { this.database = database; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    /**
     * HBase 连接配置。
     */
    public static class HBase {
        /** ZooKeeper 地址 */
        private String zkQuorum = "localhost";
        /** ZooKeeper 端口 */
        private String zkPort = "2181";
        /** 特征表名 */
        private String tableName = "customer_feature";
        /** 列族名 */
        private String columnFamily = "cf";
        /** RPC 超时 (ms) */
        private int rpcTimeoutMs = 5000;
        /** 操作超时 (ms) */
        private int operationTimeoutMs = 10000;

        public String getZkQuorum() { return zkQuorum; }
        public void setZkQuorum(String zkQuorum) { this.zkQuorum = zkQuorum; }
        public String getZkPort() { return zkPort; }
        public void setZkPort(String zkPort) { this.zkPort = zkPort; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnFamily() { return columnFamily; }
        public void setColumnFamily(String columnFamily) { this.columnFamily = columnFamily; }
        public int getRpcTimeoutMs() { return rpcTimeoutMs; }
        public void setRpcTimeoutMs(int rpcTimeoutMs) { this.rpcTimeoutMs = rpcTimeoutMs; }
        public int getOperationTimeoutMs() { return operationTimeoutMs; }
        public void setOperationTimeoutMs(int operationTimeoutMs) { this.operationTimeoutMs = operationTimeoutMs; }
    }

    /**
     * 缓存策略配置。
     */
    public static class Cache {
        /** Redis 缓存 TTL (小时) — HBase 命中后回写 Redis 的过期时间 */
        private int redisTtlHours = 1;
        /** 特征类型列表 — 对应 Redis Key 中的 featureType 部分 */
        private String[] featureTypes = {
            "credit_query_3m", "overdue_6m", "apply_freq_1m",
            "transaction_summary_1h", "credit_score", "risk_level"
        };
        /** 单次查询超时 (ms) */
        private int queryTimeoutMs = 200;
        /** 批量查询最大重试次数 */
        private int maxRetries = 1;

        public int getRedisTtlHours() { return redisTtlHours; }
        public void setRedisTtlHours(int redisTtlHours) { this.redisTtlHours = redisTtlHours; }
        public String[] getFeatureTypes() { return featureTypes; }
        public void setFeatureTypes(String[] featureTypes) { this.featureTypes = featureTypes; }
        public int getQueryTimeoutMs() { return queryTimeoutMs; }
        public void setQueryTimeoutMs(int queryTimeoutMs) { this.queryTimeoutMs = queryTimeoutMs; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }
}
