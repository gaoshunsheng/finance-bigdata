package com.credit.platform.server.adapter;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部 API 适配器配置属性。
 *
 * <pre>
 * decision:
 *   engine:
 *     external-api:
 *       enabled: true
 *       mock-mode: true
 *       timeout-ms: 3000
 *       retry-count: 1
 *       cache-ttl-ms: 3600000
 *       adapters:
 *         credit-bureau:
 *           enabled: true
 *           mock-mode: true
 *           url: http://credit-bureau-api:8080
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "decision.engine.external-api")
public class ExternalApiProperties {

    /** 是否启用外部 API 调用（全局开关） */
    private boolean enabled = true;

    /** 全局 Mock 模式：为 true 时所有适配器返回 Mock 数据 */
    private boolean mockMode = true;

    /** 调用超时（毫秒），默认 3 秒 */
    private long timeoutMs = 3000;

    /** 重试次数，默认 1 次 */
    private int retryCount = 1;

    /** 降级缓存 TTL（毫秒），默认 1 小时 */
    private long cacheTtlMs = 3600000;

    /** 各适配器的独立配置 */
    private Map<String, AdapterConfig> adapters = new HashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isMockMode() { return mockMode; }
    public void setMockMode(boolean mockMode) { this.mockMode = mockMode; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public long getCacheTtlMs() { return cacheTtlMs; }
    public void setCacheTtlMs(long cacheTtlMs) { this.cacheTtlMs = cacheTtlMs; }

    public Map<String, AdapterConfig> getAdapters() { return adapters; }
    public void setAdapters(Map<String, AdapterConfig> adapters) { this.adapters = adapters; }

    /**
     * 获取指定适配器的配置，返回 null 时使用全局默认值。
     */
    public AdapterConfig getAdapterConfig(String adapterType) {
        return adapters.get(adapterType);
    }

    /**
     * 单个适配器的配置。
     */
    public static class AdapterConfig {
        private boolean enabled = true;
        private boolean mockMode = true;
        private String url;
        private String apiKey;
        private long timeoutMs;
        private int retryCount = -1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isMockMode() { return mockMode; }
        public void setMockMode(boolean mockMode) { this.mockMode = mockMode; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    }
}
