package com.credit.platform.engine.sdk;

/**
 * SDK 配置属性。
 * <p>
 * 定义连接决策引擎服务所需的全部配置参数。
 * 可通过 Spring Boot 的 application.yml 映射或手动构建。
 * </p>
 *
 * <pre>
 * // 手动构建
 * DecisionClientConfig config = DecisionClientConfig.builder()
 *     .endpoint("http://localhost:8080")
 *     .connectTimeoutMs(3000)
 *     .readTimeoutMs(5000)
 *     .maxRetries(2)
 *     .apiKey("your-api-key")
 *     .build();
 * </pre>
 *
 * <pre>
 * # application.yml
 * decision:
 *   client:
 *     endpoint: http://localhost:8080
 *     connect-timeout-ms: 3000
 *     read-timeout-ms: 5000
 *     max-retries: 2
 *     api-key: your-api-key
 * </pre>
 */
public class DecisionClientConfig {

    /** 默认连接超时: 3 秒 */
    public static final long DEFAULT_CONNECT_TIMEOUT_MS = 3000;

    /** 默认读取超时: 5 秒 */
    public static final long DEFAULT_READ_TIMEOUT_MS = 5000;

    /** 默认最大重试次数 */
    public static final int DEFAULT_MAX_RETRIES = 1;

    private final String endpoint;
    private final long connectTimeoutMs;
    private final long readTimeoutMs;
    private final int maxRetries;
    private final String apiKey;
    private final String channel;
    private final boolean enabled;

    private DecisionClientConfig(Builder builder) {
        this.endpoint = builder.endpoint;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.maxRetries = builder.maxRetries;
        this.apiKey = builder.apiKey;
        this.channel = builder.channel;
        this.enabled = builder.enabled;
    }

    public String getEndpoint() { return endpoint; }
    public long getConnectTimeoutMs() { return connectTimeoutMs; }
    public long getReadTimeoutMs() { return readTimeoutMs; }
    public int getMaxRetries() { return maxRetries; }
    public String getApiKey() { return apiKey; }
    public String getChannel() { return channel; }
    public boolean isEnabled() { return enabled; }

    /**
     * 获取决策执行 API 的完整 URL。
     */
    public String getDecisionUrl() {
        return normalizeEndpoint() + "/api/v1/decision/execute";
    }

    /**
     * 获取决策报告 API 的完整 URL。
     */
    public String getReportUrl(String decisionId) {
        return normalizeEndpoint() + "/api/v1/decision/report/" + decisionId;
    }

    private String normalizeEndpoint() {
        if (endpoint == null) return "http://localhost:8080";
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String endpoint = "http://localhost:8080";
        private long connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        private long readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private String apiKey;
        private String channel = "SDK";
        private boolean enabled = true;

        public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
        public Builder connectTimeoutMs(long ms) { this.connectTimeoutMs = ms; return this; }
        public Builder readTimeoutMs(long ms) { this.readTimeoutMs = ms; return this; }
        public Builder maxRetries(int n) { this.maxRetries = n; return this; }
        public Builder apiKey(String key) { this.apiKey = key; return this; }
        public Builder channel(String channel) { this.channel = channel; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public DecisionClientConfig build() { return new DecisionClientConfig(this); }
    }

    @Override
    public String toString() {
        return "DecisionClientConfig{endpoint='" + endpoint
            + "', connectTimeout=" + connectTimeoutMs + "ms"
            + ", readTimeout=" + readTimeoutMs + "ms"
            + ", channel='" + channel + "'}";
    }
}
