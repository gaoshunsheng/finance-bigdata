package com.credit.platform.engine.core.model;

import java.util.Objects;

/**
 * 模型服务配置。
 * <p>
 * 定义模型推理服务的连接参数、超时、重试和降级策略。
 * 配合 {@link ModelServiceClient} 使用。
 * </p>
 *
 * <pre>
 * ModelConfig config = ModelConfig.builder()
 *     .endpoint("http://model-service:8080/api/v1/predict")
 *     .modelId("MOD_ANTI_FRAUD_V2")
 *     .timeoutMs(3000)
 *     .maxRetries(1)
 *     .mockEnabled(false)
 *     .build();
 * </pre>
 */
public final class ModelConfig {

    /** 默认超时时间: 3 秒 */
    public static final long DEFAULT_TIMEOUT_MS = 3000;

    /** 默认最大重试次数 */
    public static final int DEFAULT_MAX_RETRIES = 1;

    private final String modelId;
    private final String endpoint;
    private final long timeoutMs;
    private final int maxRetries;
    private final boolean mockEnabled;
    private final double mockScore;

    private ModelConfig(Builder builder) {
        this.modelId = Objects.requireNonNull(builder.modelId, "modelId must not be null");
        this.endpoint = builder.endpoint;
        this.timeoutMs = builder.timeoutMs > 0 ? builder.timeoutMs : DEFAULT_TIMEOUT_MS;
        this.maxRetries = builder.maxRetries >= 0 ? builder.maxRetries : DEFAULT_MAX_RETRIES;
        this.mockEnabled = builder.mockEnabled;
        this.mockScore = builder.mockScore;
    }

    public String getModelId() { return modelId; }
    public String getEndpoint() { return endpoint; }
    public long getTimeoutMs() { return timeoutMs; }
    public int getMaxRetries() { return maxRetries; }
    public boolean isMockEnabled() { return mockEnabled; }
    public double getMockScore() { return mockScore; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String modelId;
        private String endpoint;
        private long timeoutMs = DEFAULT_TIMEOUT_MS;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private boolean mockEnabled = false;
        private double mockScore = 0.5;

        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder endpoint(String endpoint) { this.endpoint = endpoint; return this; }
        public Builder timeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder mockEnabled(boolean enabled) { this.mockEnabled = enabled; return this; }
        public Builder mockScore(double score) { this.mockScore = score; return this; }
        public ModelConfig build() { return new ModelConfig(this); }
    }

    @Override
    public String toString() {
        return "ModelConfig{modelId='" + modelId + "', endpoint='" + endpoint
            + "', timeout=" + timeoutMs + "ms, mock=" + mockEnabled + '}';
    }
}
