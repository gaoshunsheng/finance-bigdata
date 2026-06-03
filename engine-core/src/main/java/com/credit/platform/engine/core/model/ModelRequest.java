package com.credit.platform.engine.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 模型推理请求。
 * <p>
 * 封装发送给模型推理服务的输入数据，包含模型 ID 和特征向量。
 * </p>
 *
 * <pre>
 * ModelRequest request = ModelRequest.builder()
 *     .modelId("MOD_ANTI_FRAUD_V2")
 *     .features(Map.of("age", 28, "income", 50000, "overdue_count_6m", 0))
 *     .build();
 * </pre>
 */
public final class ModelRequest {

    private final String modelId;
    private final String requestId;
    private final Map<String, Object> features;

    private ModelRequest(Builder builder) {
        this.modelId = Objects.requireNonNull(builder.modelId, "modelId must not be null");
        this.requestId = builder.requestId;
        this.features = Collections.unmodifiableMap(
            new LinkedHashMap<>(builder.features));
    }

    public String getModelId() { return modelId; }
    public String getRequestId() { return requestId; }
    public Map<String, Object> getFeatures() { return features; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String modelId;
        private String requestId;
        private Map<String, Object> features = new LinkedHashMap<>();

        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder requestId(String requestId) { this.requestId = requestId; return this; }

        public Builder features(Map<String, Object> features) {
            this.features = features != null ? new LinkedHashMap<>(features) : new LinkedHashMap<>();
            return this;
        }

        public Builder addFeature(String name, Object value) {
            this.features.put(name, value);
            return this;
        }

        public ModelRequest build() { return new ModelRequest(this); }
    }

    @Override
    public String toString() {
        return "ModelRequest{modelId='" + modelId + "', features=" + features.keySet() + '}';
    }
}
