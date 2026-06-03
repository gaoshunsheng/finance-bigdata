package com.credit.platform.engine.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型推理响应。
 * <p>
 * 封装模型推理服务返回的结果，包含预测分数、概率和附加输出。
 * </p>
 *
 * <pre>
 * ModelResponse response = ModelResponse.builder()
 *     .modelId("MOD_ANTI_FRAUD_V2")
 *     .score(0.82)
 *     .probability(0.82)
 *     .label("LOW_RISK")
 *     .output("credit_limit", 50000)
 *     .build();
 * </pre>
 */
public final class ModelResponse {

    private final String modelId;
    private final double score;
    private final double probability;
    private final String label;
    private final Map<String, Object> outputs;
    private final boolean success;
    private final String errorMessage;
    private final long latencyMs;

    private ModelResponse(Builder builder) {
        this.modelId = builder.modelId;
        this.score = builder.score;
        this.probability = builder.probability;
        this.label = builder.label;
        this.outputs = Collections.unmodifiableMap(
            new LinkedHashMap<>(builder.outputs));
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.latencyMs = builder.latencyMs;
    }

    public String getModelId() { return modelId; }
    public double getScore() { return score; }
    public double getProbability() { return probability; }
    public String getLabel() { return label; }
    public Map<String, Object> getOutputs() { return outputs; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public long getLatencyMs() { return latencyMs; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String modelId;
        private double score;
        private double probability;
        private String label;
        private Map<String, Object> outputs = new LinkedHashMap<>();
        private boolean success = true;
        private String errorMessage;
        private long latencyMs;

        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder score(double score) { this.score = score; return this; }
        public Builder probability(double probability) { this.probability = probability; return this; }
        public Builder label(String label) { this.label = label; return this; }
        public Builder outputs(Map<String, Object> outputs) {
            this.outputs = outputs != null ? new LinkedHashMap<>(outputs) : new LinkedHashMap<>();
            return this;
        }
        public Builder output(String key, Object value) {
            this.outputs.put(key, value);
            return this;
        }
        public Builder success(boolean success) { this.success = success; return this; }
        public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }
        public Builder latencyMs(long ms) { this.latencyMs = ms; return this; }
        public ModelResponse build() { return new ModelResponse(this); }
    }

    /**
     * 创建一个错误响应。
     *
     * @param modelId      模型 ID
     * @param errorMessage 错误信息
     * @return 错误响应
     */
    public static ModelResponse error(String modelId, String errorMessage) {
        return builder()
            .modelId(modelId)
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    @Override
    public String toString() {
        return "ModelResponse{modelId='" + modelId + "', score=" + score
            + ", label='" + label + "', success=" + success + '}';
    }
}
