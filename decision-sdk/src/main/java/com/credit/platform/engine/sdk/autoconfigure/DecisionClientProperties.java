package com.credit.platform.engine.sdk.autoconfigure;

import com.credit.platform.engine.sdk.DecisionClientConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot 配置属性 — 映射 application.yml 中的 decision.client.* 配置。
 * <p>
 * 使用示例 (application.yml):
 * <pre>
 * decision:
 *   client:
 *     endpoint: http://localhost:8080
 *     connect-timeout-ms: 3000
 *     read-timeout-ms: 5000
 *     max-retries: 1
 *     api-key: your-api-key
 *     channel: SDK
 *     enabled: true
 * </pre>
 * </p>
 */
@ConfigurationProperties(prefix = "decision.client")
public class DecisionClientProperties {

    /** 决策引擎服务地址 */
    private String endpoint = "http://localhost:8080";

    /** 连接超时 (毫秒) */
    private long connectTimeoutMs = DecisionClientConfig.DEFAULT_CONNECT_TIMEOUT_MS;

    /** 读取超时 (毫秒) */
    private long readTimeoutMs = DecisionClientConfig.DEFAULT_READ_TIMEOUT_MS;

    /** 最大重试次数 */
    private int maxRetries = DecisionClientConfig.DEFAULT_MAX_RETRIES;

    /** API Key */
    private String apiKey;

    /** 渠道标识 */
    private String channel = "SDK";

    /** 是否启用 */
    private boolean enabled = true;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public long getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(long connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public long getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(long readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * 转换为 SDK 内部配置对象。
     */
    public DecisionClientConfig toConfig() {
        return DecisionClientConfig.builder()
            .endpoint(endpoint)
            .connectTimeoutMs(connectTimeoutMs)
            .readTimeoutMs(readTimeoutMs)
            .maxRetries(maxRetries)
            .apiKey(apiKey)
            .channel(channel)
            .enabled(enabled)
            .build();
    }
}
